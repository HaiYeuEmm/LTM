package vn.onlineexam.client;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.List;

/** Plain TCP chat connection. Wire format: one UTF-8 JSON object per line. */
public final class ClassChatSocket {
    private static final Gson GSON = new Gson();
    private static final int CHAT_PORT = Integer.getInteger("onlineexam.chat.port", 2020);
    private final String host;
    private final String token;
    private final long classId;
    private final Consumer<ChatMessage> onMessage;
    private final Consumer<Throwable> onError;
    private final ExecutorService io = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "class-chat-socket");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Socket socket;
    private volatile BufferedWriter writer;
    private final LinkedBlockingQueue<String> outbound = new LinkedBlockingQueue<>();

    public ClassChatSocket(String apiBaseUrl, String token, long classId,
                           Consumer<ChatMessage> onMessage, Consumer<Throwable> onError) {
        this.host = URI.create(apiBaseUrl).getHost();
        this.token = token;
        this.classId = classId;
        this.onMessage = onMessage;
        this.onError = onError;
    }

    public void connect() {
        io.submit(() -> {
            try {
                Socket connection = new Socket(host, CHAT_PORT);
                socket = connection;
                writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                outbound.offer(GSON.toJson(new AuthRequest("AUTH", token, classId)));
                io.submit(this::writeLoop);
                String line;
                while ((line = reader.readLine()) != null) {
                    ChatMessage message = GSON.fromJson(line, ChatMessage.class);
                    if ("READY".equals(message.type)) continue;
                    if ("ERROR".equals(message.type))
                        onError.accept(new IllegalStateException(message.message == null ? message.type : message.message));
                    else onMessage.accept(message);
                }
            } catch (Exception error) {
                if (!Thread.currentThread().isInterrupted()) onError.accept(error);
            } finally { closeSocket(); }
        });
    }

    public void send(String content) {
        outbound.offer(GSON.toJson(new MessageRequest(content)));
    }

    private void writeLoop() {
        try {
            String line;
            while (!Thread.currentThread().isInterrupted() && (line = outbound.take()) != null) {
                BufferedWriter output = writer;
                if (output == null) continue;
                synchronized (output) { output.write(line); output.newLine(); output.flush(); }
            }
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        catch (Exception error) { if (!Thread.currentThread().isInterrupted()) onError.accept(error); }
    }

    public void close() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
        io.shutdownNow();
    }

    private void closeSocket() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
        socket = null;
        writer = null;
    }

    private record AuthRequest(String type, String token, long classId) { }
    private record MessageRequest(String content) { }
    public static final class ChatMessage {
        long id;
        String senderName;
        long senderId;
        String content;
        String createdAt;
        String type;
        String message;
        long assignmentId;
        List<AssignmentInfo> assignments;
        public String senderName() { return senderName; }
        public String content() { return content; }
        public String type() { return type; }
        public long assignmentId() { return assignmentId; }
        public List<AssignmentInfo> assignments() { return assignments; }
    }

    public static final class AssignmentInfo {
        long id;
        String title;
        String description;
        String fileName;
        long fileSize;
        String createdAt;
        public long id() { return id; }
        public String title() { return title; }
        public String description() { return description; }
        public String fileName() { return fileName; }
        public long fileSize() { return fileSize; }
        public String createdAt() { return createdAt; }
    }
}
