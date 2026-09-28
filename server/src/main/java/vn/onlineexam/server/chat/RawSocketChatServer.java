package vn.onlineexam.server.chat;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** TCP chat server: one UTF-8 JSON object per line. No HTTP/WebSocket protocol is used here. */
@Component
public class RawSocketChatServer {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final int port;
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final Map<Long, Set<ClientConnection>> rooms = new ConcurrentHashMap<>();
    private volatile ServerSocket serverSocket;

    public RawSocketChatServer(JdbcTemplate jdbc, ObjectMapper json,
                               @Value("${socket.chat.port:2020}") int port) {
        this.jdbc = jdbc;
        this.json = json;
        this.port = port;
    }

    @PostConstruct
    public void start() {
        workers.submit(() -> {
            try (ServerSocket listener = new ServerSocket(port)) {
                serverSocket = listener;
                System.out.println("Raw TCP chat listening on 0.0.0.0:" + port);
                while (!listener.isClosed()) {
                    Socket socket = listener.accept();
                    workers.submit(() -> handle(socket));
                }
            } catch (Exception error) {
                if (serverSocket != null && !serverSocket.isClosed())
                    System.err.println("TCP chat server stopped: " + error.getMessage());
            }
        });
    }

    private void handle(Socket socket) {
        ClientConnection connection = null;
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String authLine = reader.readLine();
            if (authLine == null) return;
            JsonNode auth = json.readTree(authLine);
            long classId = auth.path("classId").asLong(-1);
            String token = auth.path("token").asText("");
            Account account = authenticate(token, classId);
            if (account == null) { write(writer, Map.of("type", "ERROR", "message", "Unauthorized")); return; }
            connection = new ClientConnection(classId, account.id(), writer);
            rooms.computeIfAbsent(classId, ignored -> ConcurrentHashMap.newKeySet()).add(connection);
            connection.send(Map.of("type", "READY"));
            connection.send(new SocketEvent("ASSIGNMENTS_SNAPSHOT", 0, assignmentSnapshot(classId)));
            String line;
            while ((line = reader.readLine()) != null) {
                JsonNode message = json.readTree(line);
                String content = message.path("content").asText("").trim();
                if (content.isEmpty() || content.length() > 4000) continue;
                broadcastPersisted(classId, account.id(), content);
            }
        } catch (Exception error) {
            System.err.println("TCP chat client disconnected: " + error.getMessage());
        } finally {
            if (connection != null) remove(connection);
        }
    }

    private Account authenticate(String token, long classId) throws Exception {
        if (token.isBlank() || classId < 1) return null;
        String hash = sha256(token);
        var accounts = jdbc.query("""
                SELECT u.id,u.role FROM auth_sessions s JOIN users u ON u.id=s.user_id
                WHERE s.token_hash=? AND s.revoked_at IS NULL
                  AND s.expires_at>CURRENT_TIMESTAMP(3) AND u.status='ACTIVE'
                """, (rs, row) -> new Account(rs.getLong("id"), rs.getString("role")), hash);
        if (accounts.isEmpty()) return null;
        Account account = accounts.getFirst();
        Integer allowed = "TEACHER".equals(account.role())
                ? jdbc.queryForObject("SELECT COUNT(*) FROM classrooms WHERE id=? AND owner_teacher_id=? AND status='ACTIVE'",
                    Integer.class, classId, account.id())
                : "STUDENT".equals(account.role())
                    ? jdbc.queryForObject("SELECT COUNT(*) FROM class_memberships m JOIN classrooms c ON c.id=m.classroom_id WHERE m.classroom_id=? AND m.student_id=? AND m.status='ACTIVE' AND c.status='ACTIVE'",
                        Integer.class, classId, account.id()) : 0;
        return allowed != null && allowed > 0 ? account : null;
    }

    private void broadcastPersisted(long classId, long senderId, String content) throws Exception {
        Long roomId = jdbc.queryForObject("SELECT id FROM chat_rooms WHERE classroom_id=?", Long.class, classId);
        String role = jdbc.queryForObject("SELECT role FROM users WHERE id=?", String.class, senderId);
        jdbc.update("""
                INSERT INTO chat_room_members(room_id,user_id,member_role,status,joined_at,left_at)
                VALUES(?,?,?,'ACTIVE',CURRENT_TIMESTAMP(3),NULL)
                ON DUPLICATE KEY UPDATE status='ACTIVE',joined_at=CURRENT_TIMESTAMP(3),left_at=NULL
                """, roomId, senderId, role);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO chat_messages(room_id,sender_id,request_id,content) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, roomId);
            statement.setLong(2, senderId);
            statement.setString(3, UUID.randomUUID().toString());
            statement.setString(4, content);
            return statement;
        }, key);
        ChatMessage saved = jdbc.queryForObject("""
                SELECT m.id,u.full_name AS sender_name,m.sender_id,m.content,m.created_at
                FROM chat_messages m JOIN users u ON u.id=m.sender_id WHERE m.id=?
                """, (rs, row) -> new ChatMessage(rs.getLong("id"), rs.getString("sender_name"),
                rs.getLong("sender_id"), rs.getString("content"), rs.getTimestamp("created_at").toInstant().toString()), key.getKey().longValue());
        String payload = json.writeValueAsString(saved);
        for (ClientConnection peer : rooms.getOrDefault(classId, Set.of())) peer.sendRaw(payload);
    }

    public void broadcastAssignmentChanged(long classId, String eventType, long assignmentId) {
        String payload;
        try { payload = json.writeValueAsString(new SocketEvent(eventType, assignmentId, assignmentSnapshot(classId))); }
        catch (Exception error) { throw new IllegalStateException("Could not encode socket event", error); }
        for (ClientConnection peer : rooms.getOrDefault(classId, Set.of())) peer.sendRaw(payload);
    }

    private java.util.List<AssignmentSnapshot> assignmentSnapshot(long classId) {
        return jdbc.query("""
                SELECT id,title,description,original_file_name,file_size,created_at
                FROM class_assignments WHERE classroom_id=? ORDER BY created_at DESC,id DESC
                """, (rs, row) -> new AssignmentSnapshot(rs.getLong("id"), rs.getString("title"),
                rs.getString("description"), rs.getString("original_file_name"), rs.getLong("file_size"),
                rs.getTimestamp("created_at").toInstant().toString()), classId);
    }

    private void remove(ClientConnection connection) {
        Set<ClientConnection> peers = rooms.get(connection.classId());
        if (peers != null) { peers.remove(connection); if (peers.isEmpty()) rooms.remove(connection.classId(), peers); }
    }

    private static String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private static void write(BufferedWriter writer, Object value) throws Exception {
        writer.write(new ObjectMapper().writeValueAsString(value)); writer.newLine(); writer.flush();
    }

    @PreDestroy
    public void stop() throws Exception {
        if (serverSocket != null) serverSocket.close();
        workers.shutdownNow();
    }

    private record Account(long id, String role) { }
    private record ChatMessage(long id, String senderName, long senderId, String content, String createdAt) { }
    private record SocketEvent(String type, long assignmentId, java.util.List<AssignmentSnapshot> assignments) { }
    private record AssignmentSnapshot(long id, String title, String description, String fileName,
                                      long fileSize, String createdAt) { }

    private final class ClientConnection {
        private final long classId;
        private final long userId;
        private final BufferedWriter writer;
        private ClientConnection(long classId, long userId, BufferedWriter writer) {
            this.classId = classId; this.userId = userId; this.writer = writer;
        }
        long classId() { return classId; }
        @SuppressWarnings("unused") long userId() { return userId; }
        synchronized void send(Object value) {
            try { sendRaw(json.writeValueAsString(value)); } catch (Exception error) { }
        }
        synchronized void sendRaw(String payload) {
            try { writer.write(payload); writer.newLine(); writer.flush(); } catch (Exception error) { }
        }
    }
}
