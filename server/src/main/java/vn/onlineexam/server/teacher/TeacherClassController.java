package vn.onlineexam.server.teacher;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.onlineexam.server.auth.TeacherAuthInterceptor;
import vn.onlineexam.server.chat.RawSocketChatServer;

@RestController
@RequestMapping("/api/teacher/classes/{classId}")
public class TeacherClassController {
    private static final long MAX_FILE_BYTES = 15L * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RawSocketChatServer chatSocketServer;

    public TeacherClassController(JdbcTemplate jdbc, TransactionTemplate transactions, RawSocketChatServer chatSocketServer) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.chatSocketServer = chatSocketServer;
    }

    @PostMapping("/members/bulk")
    public List<Member> addMembers(@PathVariable long classId, @RequestBody AddMembersRequest input,
                                  HttpServletRequest request) {
        long teacherId = teacherId(request);
        requireOwnedClass(classId, teacherId);
        if (input.emails() == null || input.emails().isEmpty() || input.emails().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gửi từ 1 đến 100 email học sinh.");
        }
        List<String> emails = input.emails().stream().filter(java.util.Objects::nonNull)
                .map(email -> email.trim().toLowerCase(Locale.ROOT)).filter(email -> !email.isBlank()).distinct().toList();
        List<Student> students = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String email : emails) {
            List<Student> found = jdbc.query("""
                    SELECT id,full_name,email FROM users
                    WHERE email=? AND role='STUDENT' AND status='ACTIVE'
                    """, (rs, row) -> new Student(rs.getLong("id"), rs.getString("full_name"), rs.getString("email")), email);
            if (found.isEmpty()) missing.add(email); else students.add(found.getFirst());
        }
        if (emails.isEmpty() || !missing.isEmpty()) {
            String message = emails.isEmpty() ? "Danh sách email trống."
                    : "Không tìm thấy học sinh đang hoạt động: " + String.join(", ", missing);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        transactions.executeWithoutResult(status -> {
            long roomId = ensureChatRoom(classId, teacherId);
            upsertChatMember(roomId, teacherId, "TEACHER");
            for (Student student : students) {
                jdbc.update("""
                        INSERT INTO class_memberships(classroom_id,student_id,status,joined_at,removed_at)
                        VALUES(?,?,'ACTIVE',CURRENT_TIMESTAMP(3),NULL)
                        ON DUPLICATE KEY UPDATE status='ACTIVE',joined_at=CURRENT_TIMESTAMP(3),removed_at=NULL
                        """, classId, student.id());
                upsertChatMember(roomId, student.id(), "STUDENT");
            }
        });
        return students.stream().map(student -> new Member(student.id(), student.name(), student.email(), "ACTIVE")).toList();
    }

    @GetMapping("/messages")
    public List<ChatMessage> messages(@PathVariable long classId, HttpServletRequest request) {
        long teacherId = teacherId(request);
        requireOwnedClass(classId, teacherId);
        long roomId = ensureChatRoom(classId, teacherId);
        List<ChatMessage> latest = jdbc.query("""
                SELECT m.id,u.full_name AS sender_name,m.sender_id,m.content,m.created_at
                FROM chat_messages m JOIN users u ON u.id=m.sender_id
                WHERE m.room_id=? AND m.deleted_at IS NULL
                ORDER BY m.created_at DESC,m.id DESC LIMIT 100
                """, (rs, row) -> new ChatMessage(rs.getLong("id"), rs.getString("sender_name"),
                rs.getLong("sender_id"), rs.getString("content"), rs.getTimestamp("created_at").toInstant().toString()), roomId);
        return latest.stream().sorted(Comparator.comparing(ChatMessage::createdAt)).toList();
    }

    @PostMapping("/messages")
    public ChatMessage sendMessage(@PathVariable long classId, @RequestBody MessageRequest input,
                                  HttpServletRequest request) {
        long teacherId = teacherId(request);
        requireOwnedClass(classId, teacherId);
        String content = input.content() == null ? "" : input.content().trim();
        if (content.isEmpty() || content.length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tin nhắn cần có nội dung và tối đa 4000 ký tự.");
        }
        long roomId = ensureChatRoom(classId, teacherId);
        upsertChatMember(roomId, teacherId, "TEACHER");
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO chat_messages(room_id,sender_id,request_id,content) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, roomId);
            statement.setLong(2, teacherId);
            statement.setString(3, UUID.randomUUID().toString());
            statement.setString(4, content);
            return statement;
        }, key);
        Long messageId = key.getKey().longValue();
        String senderName = jdbc.queryForObject("SELECT full_name FROM users WHERE id=?", String.class, teacherId);
        return new ChatMessage(messageId, senderName, teacherId, content, java.time.Instant.now().toString());
    }

    @GetMapping("/assignments")
    public List<Assignment> assignments(@PathVariable long classId, HttpServletRequest request) {
        requireOwnedClass(classId, teacherId(request));
        return jdbc.query("""
                SELECT a.id,a.title,a.description,a.original_file_name,a.file_size,u.full_name AS teacher_name,a.created_at
                FROM class_assignments a JOIN users u ON u.id=a.teacher_id
                WHERE a.classroom_id=? ORDER BY a.created_at DESC
                """, (rs, row) -> new Assignment(rs.getLong("id"), rs.getString("title"),
                rs.getString("description"), rs.getString("original_file_name"), rs.getLong("file_size"),
                rs.getString("teacher_name"), rs.getTimestamp("created_at").toInstant().toString()), classId);
    }

    @PostMapping(path = "/assignments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Assignment uploadAssignment(@PathVariable long classId, @RequestParam String title,
            @RequestParam(required = false) String description, @RequestPart MultipartFile file,
            HttpServletRequest request) {
        long teacherId = teacherId(request);
        requireOwnedClass(classId, teacherId);
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty() || cleanTitle.length() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề bài tập bắt buộc, tối đa 180 ký tự.");
        }
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hãy chọn tệp bài tập.");
        if (file.getSize() > MAX_FILE_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp tải lên tối đa 15 MB.");
        String originalName = java.nio.file.Paths.get(file.getOriginalFilename() == null ? "assignment" : file.getOriginalFilename())
                .getFileName().toString();
        String contentType = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        KeyHolder key = new GeneratedKeyHolder();
        try {
            byte[] bytes = file.getBytes();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO class_assignments(classroom_id,teacher_id,title,description,original_file_name,content_type,file_size,file_data)
                        VALUES(?,?,?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, classId);
                statement.setLong(2, teacherId);
                statement.setString(3, cleanTitle);
                statement.setString(4, description == null ? null : description.trim());
                statement.setString(5, originalName);
                statement.setString(6, contentType);
                statement.setLong(7, bytes.length);
                statement.setBytes(8, bytes);
                return statement;
            }, key);
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được tệp tải lên.");
        }
        String teacherName = jdbc.queryForObject("SELECT full_name FROM users WHERE id=?", String.class, teacherId);
        long assignmentId = key.getKey().longValue();
        chatSocketServer.broadcastAssignmentChanged(classId, "ASSIGNMENT_CREATED", assignmentId);
        return new Assignment(assignmentId, cleanTitle, description, originalName, file.getSize(),
                teacherName, java.time.Instant.now().toString());
    }

    @GetMapping("/assignments/{assignmentId}/file")
    public ResponseEntity<byte[]> downloadAssignment(@PathVariable long classId, @PathVariable long assignmentId,
                                                     HttpServletRequest request) {
        requireOwnedClass(classId, teacherId(request));
        List<AssignmentFile> files = jdbc.query("""
                SELECT original_file_name,content_type,file_data FROM class_assignments
                WHERE id=? AND classroom_id=?
                """, (rs, row) -> new AssignmentFile(rs.getString("original_file_name"),
                rs.getString("content_type"), rs.getBytes("file_data")), assignmentId, classId);
        if (files.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp bài tập.");
        AssignmentFile file = files.getFirst();
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(file.contentType()); }
        catch (RuntimeException exception) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable long classId, @PathVariable long assignmentId,
                                                  HttpServletRequest request) {
        requireOwnedClass(classId, teacherId(request));
        int deleted = jdbc.update("DELETE FROM class_assignments WHERE id=? AND classroom_id=?", assignmentId, classId);
        if (deleted == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài tập trong lớp này.");
        chatSocketServer.broadcastAssignmentChanged(classId, "ASSIGNMENT_DELETED", assignmentId);
        return ResponseEntity.noContent().build();
    }

    private long ensureChatRoom(long classId, long teacherId) {
        String className = jdbc.queryForObject("SELECT name FROM classrooms WHERE id=?", String.class, classId);
        jdbc.update("""
                INSERT INTO chat_rooms(classroom_id,created_by,name) VALUES(?,?,?)
                ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)
                """, classId, teacherId, className + " - Chat lớp");
        return jdbc.queryForObject("SELECT id FROM chat_rooms WHERE classroom_id=?", Long.class, classId);
    }

    private void upsertChatMember(long roomId, long userId, String role) {
        jdbc.update("""
                INSERT INTO chat_room_members(room_id,user_id,member_role,status,joined_at,left_at)
                VALUES(?,?,?,'ACTIVE',CURRENT_TIMESTAMP(3),NULL)
                ON DUPLICATE KEY UPDATE status='ACTIVE',joined_at=CURRENT_TIMESTAMP(3),left_at=NULL
                """, roomId, userId, role);
    }

    private void requireOwnedClass(long classId, long teacherId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM classrooms WHERE id=? AND owner_teacher_id=?",
                Integer.class, classId, teacherId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp của bạn.");
    }

    private static long teacherId(HttpServletRequest request) {
        return (long) request.getAttribute(TeacherAuthInterceptor.USER_ID_ATTRIBUTE);
    }

    public record AddMembersRequest(List<String> emails) { }
    public record Member(long id, String fullName, String email, String status) { }
    public record MessageRequest(String content) { }
    public record ChatMessage(long id, String senderName, long senderId, String content, String createdAt) { }
    public record Assignment(long id, String title, String description, String fileName, long fileSize,
                             String teacherName, String createdAt) { }
    private record Student(long id, String name, String email) { }
    private record AssignmentFile(String name, String contentType, byte[] bytes) { }
}
