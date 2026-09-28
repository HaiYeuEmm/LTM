package vn.onlineexam.server.student;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.onlineexam.server.auth.StudentAuthInterceptor;

@RestController
@RequestMapping("/api/student")
public class StudentController {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public StudentController(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @GetMapping("/summary")
    public Summary summary(HttpServletRequest request) {
        long studentId = studentId(request);
        return jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM class_memberships WHERE student_id=? AND status='ACTIVE') AS classes,
                  (SELECT COUNT(DISTINCT es.id) FROM class_memberships m
                   JOIN exam_sessions es ON es.classroom_id=m.classroom_id
                   JOIN exams e ON e.id=es.exam_id
                   WHERE m.student_id=? AND m.status='ACTIVE' AND e.status='PUBLISHED'
                     AND es.status='OPEN' AND es.opens_at<=CURRENT_TIMESTAMP(3) AND es.closes_at>CURRENT_TIMESTAMP(3)) AS available_exams,
                  (SELECT COUNT(*) FROM attempts a JOIN exam_results r ON r.attempt_id=a.id
                   WHERE a.student_id=?) AS completed
                """, (rs, row) -> new Summary(rs.getInt("classes"), rs.getInt("available_exams"), rs.getInt("completed")),
                studentId, studentId, studentId);
    }

    @GetMapping("/classes")
    public List<Classroom> classes(HttpServletRequest request) {
        return jdbc.query("""
                SELECT c.id,c.name,c.description,c.class_code,t.full_name AS teacher_name,
                       (SELECT COUNT(*) FROM class_assignments a WHERE a.classroom_id=c.id) AS assignment_count,
                       (SELECT COUNT(*) FROM chat_rooms r JOIN chat_messages cm ON cm.room_id=r.id
                        WHERE r.classroom_id=c.id AND cm.deleted_at IS NULL) AS message_count
                FROM class_memberships m JOIN classrooms c ON c.id=m.classroom_id
                JOIN users t ON t.id=c.owner_teacher_id
                WHERE m.student_id=? AND m.status='ACTIVE' AND c.status='ACTIVE'
                ORDER BY c.name
                """, (rs, row) -> new Classroom(rs.getLong("id"), rs.getString("name"),
                rs.getString("description"), rs.getString("class_code"), rs.getString("teacher_name"),
                rs.getInt("assignment_count"), rs.getInt("message_count")), studentId(request));
    }

    @GetMapping("/exams")
    public List<StudentExam> exams(HttpServletRequest request) {
        long studentId = studentId(request);
        return jdbc.query("""
                SELECT es.id AS session_id,e.title,e.description,c.name AS class_name,
                       es.opens_at,es.closes_at,es.duration_minutes,es.status AS session_status,
                       a.state AS attempt_state,er.score,er.max_score
                FROM class_memberships m
                JOIN classrooms c ON c.id=m.classroom_id
                JOIN exam_sessions es ON es.classroom_id=c.id
                JOIN exams e ON e.id=es.exam_id
                LEFT JOIN attempts a ON a.exam_session_id=es.id AND a.student_id=m.student_id
                LEFT JOIN exam_results er ON er.attempt_id=a.id
                WHERE m.student_id=? AND m.status='ACTIVE' AND c.status='ACTIVE'
                  AND e.status='PUBLISHED' AND es.status IN ('SCHEDULED','OPEN','CLOSED','GRADED')
                ORDER BY es.opens_at DESC
                """, (rs, row) -> new StudentExam(rs.getLong("session_id"), rs.getString("title"),
                rs.getString("description"), rs.getString("class_name"), rs.getTimestamp("opens_at").toInstant().toString(),
                rs.getTimestamp("closes_at").toInstant().toString(), rs.getInt("duration_minutes"),
                rs.getString("session_status"), rs.getString("attempt_state"),
                rs.getBigDecimal("score"), rs.getBigDecimal("max_score")), studentId);
    }

    @GetMapping("/exams/{sessionId}/questions")
    public List<QuizQuestion> quizQuestions(@PathVariable long sessionId, HttpServletRequest request) {
        long studentId = studentId(request);
        requireOpenExamMembership(sessionId, studentId);
        List<AttemptInfo> attempts = jdbc.query("SELECT id,state FROM attempts WHERE exam_session_id=? AND student_id=? ORDER BY attempt_number DESC LIMIT 1",
                (rs, row) -> new AttemptInfo(rs.getLong("id"), rs.getString("state")), sessionId, studentId);
        if (!attempts.isEmpty() && "SUBMITTED".equals(attempts.getFirst().state()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz này đã được nộp.");
        if (attempts.isEmpty()) {
            jdbc.update("INSERT INTO attempts(exam_session_id,student_id,attempt_number,state,started_at) VALUES(?,?,1,'IN_PROGRESS',CURRENT_TIMESTAMP(3))",
                    sessionId, studentId);
        }
        List<QuizQuestion> questions = jdbc.query("""
                SELECT q.id,q.content FROM exam_sessions es
                JOIN class_memberships m ON m.classroom_id=es.classroom_id AND m.student_id=? AND m.status='ACTIVE'
                JOIN exam_questions eq ON eq.exam_id=es.exam_id
                JOIN questions q ON q.id=eq.question_id AND q.status='ACTIVE'
                WHERE es.id=? ORDER BY eq.position
                """, (rs, row) -> new QuizQuestion(rs.getLong("id"), rs.getString("content"), List.of()), studentId, sessionId);
        return questions.stream().map(question -> new QuizQuestion(question.id(), question.content(),
                jdbc.query("""
                        SELECT id,content FROM question_choices WHERE question_id=? ORDER BY position
                        """, (rs, row) -> new QuizChoice(rs.getLong("id"), rs.getString("content")), question.id()))).toList();
    }

    @PostMapping("/exams/{sessionId}/submit")
    public QuizResult submitQuiz(@PathVariable long sessionId, @RequestBody QuizSubmission submission,
                                 HttpServletRequest request) {
        long studentId = studentId(request);
        requireOpenExamMembership(sessionId, studentId);
        List<Long> questionIds = jdbc.query("""
                SELECT eq.question_id FROM exam_sessions es JOIN exam_questions eq ON eq.exam_id=es.exam_id
                WHERE es.id=? ORDER BY eq.position
                """, (rs, row) -> rs.getLong(1), sessionId);
        if (questionIds.isEmpty() || submission.answers() == null || submission.answers().size() != questionIds.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hãy trả lời tất cả câu hỏi trước khi nộp.");
        java.util.Map<Long, Long> answers = new java.util.HashMap<>();
        for (QuizAnswer answer : submission.answers()) {
            if (answers.put(answer.questionId(), answer.choiceId()) != null || !questionIds.contains(answer.questionId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đáp án gửi lên không hợp lệ.");
            Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM question_choices WHERE id=? AND question_id=?",
                    Integer.class, answer.choiceId(), answer.questionId());
            if (valid == null || valid == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lựa chọn không thuộc câu hỏi.");
        }
        List<AttemptInfo> existing = jdbc.query("""
                SELECT a.id,a.state FROM attempts a WHERE a.exam_session_id=? AND a.student_id=?
                ORDER BY a.attempt_number DESC LIMIT 1
                """, (rs, row) -> new AttemptInfo(rs.getLong("id"), rs.getString("state")), sessionId, studentId);
        if (existing.isEmpty() || !"IN_PROGRESS".equals(existing.getFirst().state()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bài thi chưa được bắt đầu hoặc đã được nộp.");
        long attemptId = existing.getFirst().id();
        Integer withinTime = jdbc.queryForObject("""
                SELECT COUNT(*) FROM attempts a JOIN exam_sessions es ON es.id=a.exam_session_id
                WHERE a.id=? AND a.started_at IS NOT NULL
                  AND CURRENT_TIMESTAMP(3)<=DATE_ADD(a.started_at,INTERVAL es.duration_minutes MINUTE)
                """, Integer.class, attemptId);
        if (withinTime == null || withinTime == 0)
            throw new ResponseStatusException(HttpStatus.GONE, "Đã hết thời gian làm bài.");

        final QuizResult[] result = new QuizResult[1];
        transactions.executeWithoutResult(status -> {
            int score = 0;
            for (Long questionId : questionIds) {
                long choiceId = answers.get(questionId);
                jdbc.update("INSERT INTO attempt_answers(attempt_id,question_id,selected_choice_id) VALUES(?,?,?)",
                        attemptId, questionId, choiceId);
                Integer correct = jdbc.queryForObject("SELECT is_correct FROM question_choices WHERE id=?", Integer.class, choiceId);
                if (correct != null && correct == 1) score++;
            }
            jdbc.update("UPDATE attempts SET state='SUBMITTED',submitted_at=CURRENT_TIMESTAMP(3) WHERE id=? AND student_id=?",
                    attemptId, studentId);
            jdbc.update("INSERT INTO exam_results(attempt_id,score,max_score,graded_at) VALUES(?,?,?,CURRENT_TIMESTAMP(3))",
                    attemptId, score, questionIds.size());
            result[0] = new QuizResult(score, questionIds.size());
        });
        return result[0];
    }

    private void requireOpenExamMembership(long sessionId, long studentId) {
        Integer allowed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM exam_sessions es
                JOIN class_memberships m ON m.classroom_id=es.classroom_id AND m.student_id=? AND m.status='ACTIVE'
                JOIN classrooms c ON c.id=es.classroom_id AND c.status='ACTIVE'
                JOIN exams e ON e.id=es.exam_id AND e.status='PUBLISHED'
                WHERE es.id=? AND es.status='OPEN' AND es.opens_at<=CURRENT_TIMESTAMP(3)
                  AND es.closes_at>CURRENT_TIMESTAMP(3)
                """, Integer.class, studentId, sessionId);
        if (allowed == null || allowed == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bài thi không mở hoặc bạn không thuộc lớp này.");
    }

    @GetMapping("/results")
    public List<Result> results(HttpServletRequest request) {
        return jdbc.query("""
                SELECT r.id,e.title,c.name AS class_name,r.score,r.max_score,r.warning_count,r.graded_at
                FROM exam_results r JOIN attempts a ON a.id=r.attempt_id
                JOIN exam_sessions es ON es.id=a.exam_session_id
                JOIN exams e ON e.id=es.exam_id JOIN classrooms c ON c.id=es.classroom_id
                WHERE a.student_id=? ORDER BY r.graded_at DESC
                """, (rs, row) -> new Result(rs.getLong("id"), rs.getString("title"),
                rs.getString("class_name"), rs.getBigDecimal("score"), rs.getBigDecimal("max_score"),
                rs.getInt("warning_count"), rs.getTimestamp("graded_at").toInstant().toString()), studentId(request));
    }

    @GetMapping("/classes/{classId}/messages")
    public List<ChatMessage> messages(@PathVariable long classId, HttpServletRequest request) {
        long studentId = studentId(request);
        requireMembership(classId, studentId);
        long roomId = ensureRoom(classId, studentId);
        List<ChatMessage> latest = jdbc.query("""
                SELECT m.id,u.full_name AS sender_name,m.sender_id,m.content,m.created_at
                FROM chat_messages m JOIN users u ON u.id=m.sender_id
                WHERE m.room_id=? AND m.deleted_at IS NULL
                ORDER BY m.created_at DESC,m.id DESC LIMIT 100
                """, (rs, row) -> new ChatMessage(rs.getLong("id"), rs.getString("sender_name"),
                rs.getLong("sender_id"), rs.getString("content"), rs.getTimestamp("created_at").toInstant().toString()), roomId);
        return latest.stream().sorted(Comparator.comparing(ChatMessage::createdAt)).toList();
    }

    @PostMapping("/classes/{classId}/messages")
    public ChatMessage sendMessage(@PathVariable long classId, @RequestBody MessageRequest input,
                                   HttpServletRequest request) {
        long studentId = studentId(request);
        requireMembership(classId, studentId);
        String content = input.content() == null ? "" : input.content().trim();
        if (content.isEmpty() || content.length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tin nhắn cần có nội dung và tối đa 4000 ký tự.");
        }
        long roomId = ensureRoom(classId, studentId);
        upsertChatMember(roomId, studentId);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO chat_messages(room_id,sender_id,request_id,content) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, roomId); statement.setLong(2, studentId);
            statement.setString(3, UUID.randomUUID().toString()); statement.setString(4, content);
            return statement;
        }, key);
        String name = jdbc.queryForObject("SELECT full_name FROM users WHERE id=?", String.class, studentId);
        return new ChatMessage(key.getKey().longValue(), name, studentId, content, java.time.Instant.now().toString());
    }

    @GetMapping("/classes/{classId}/assignments")
    public List<Assignment> assignments(@PathVariable long classId, HttpServletRequest request) {
        requireMembership(classId, studentId(request));
        return jdbc.query("""
                SELECT a.id,a.title,a.description,a.original_file_name,a.file_size,u.full_name AS teacher_name,a.created_at
                FROM class_assignments a JOIN users u ON u.id=a.teacher_id
                WHERE a.classroom_id=? ORDER BY a.created_at DESC
                """, (rs, row) -> new Assignment(rs.getLong("id"), rs.getString("title"),
                rs.getString("description"), rs.getString("original_file_name"), rs.getLong("file_size"),
                rs.getString("teacher_name"), rs.getTimestamp("created_at").toInstant().toString()), classId);
    }

    @GetMapping("/classes/{classId}/assignments/{assignmentId}/file")
    public ResponseEntity<byte[]> downloadAssignment(@PathVariable long classId, @PathVariable long assignmentId,
                                                      HttpServletRequest request) {
        requireMembership(classId, studentId(request));
        List<AssignmentFile> files = jdbc.query("""
                SELECT original_file_name,content_type,file_data FROM class_assignments
                WHERE id=? AND classroom_id=?
                """, (rs, row) -> new AssignmentFile(rs.getString("original_file_name"),
                rs.getString("content_type"), rs.getBytes("file_data")), assignmentId, classId);
        if (files.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp bài tập.");
        AssignmentFile file = files.getFirst();
        MediaType type;
        try { type = MediaType.parseMediaType(file.contentType()); }
        catch (RuntimeException exception) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.name(), StandardCharsets.UTF_8).build().toString()).body(file.bytes());
    }

    private long ensureRoom(long classId, long studentId) {
        List<Long> roomIds = jdbc.query("SELECT id FROM chat_rooms WHERE classroom_id=?", (rs, row) -> rs.getLong(1), classId);
        if (!roomIds.isEmpty()) {
            long roomId = roomIds.getFirst();
            upsertChatMember(roomId, studentId);
            return roomId;
        }
        Long teacherId = jdbc.queryForObject("SELECT owner_teacher_id FROM classrooms WHERE id=?", Long.class, classId);
        String className = jdbc.queryForObject("SELECT name FROM classrooms WHERE id=?", String.class, classId);
        jdbc.update("INSERT INTO chat_rooms(classroom_id,created_by,name) VALUES(?,?,?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)",
                classId, teacherId, className + " - Chat lớp");
        Long roomId = jdbc.queryForObject("SELECT id FROM chat_rooms WHERE classroom_id=?", Long.class, classId);
        upsertChatMember(roomId, teacherId);
        upsertChatMember(roomId, studentId);
        return roomId;
    }

    private void upsertChatMember(long roomId, long userId) {
        String role = jdbc.queryForObject("SELECT role FROM users WHERE id=?", String.class, userId);
        if (!"TEACHER".equals(role) && !"STUDENT".equals(role)) return;
        jdbc.update("""
                INSERT INTO chat_room_members(room_id,user_id,member_role,status,joined_at,left_at)
                VALUES(?,?,?,'ACTIVE',CURRENT_TIMESTAMP(3),NULL)
                ON DUPLICATE KEY UPDATE status='ACTIVE',joined_at=CURRENT_TIMESTAMP(3),left_at=NULL
                """, roomId, userId, role);
    }

    private void requireMembership(long classId, long studentId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM class_memberships m JOIN classrooms c ON c.id=m.classroom_id
                WHERE m.classroom_id=? AND m.student_id=? AND m.status='ACTIVE' AND c.status='ACTIVE'
                """, Integer.class, classId, studentId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bạn không thuộc lớp này.");
    }

    private static long studentId(HttpServletRequest request) {
        return (long) request.getAttribute(StudentAuthInterceptor.USER_ID_ATTRIBUTE);
    }

    public record Summary(int classes, int availableExams, int completed) { }
    public record Classroom(long id, String name, String description, String classCode, String teacherName,
                            int assignmentCount, int messageCount) { }
    public record StudentExam(long sessionId, String title, String description, String className,
                              String opensAt, String closesAt, int durationMinutes, String sessionStatus,
                              String attemptState, java.math.BigDecimal score, java.math.BigDecimal maxScore) { }
    public record Result(long id, String title, String className, java.math.BigDecimal score,
                         java.math.BigDecimal maxScore, int warningCount, String gradedAt) { }
    public record ChatMessage(long id, String senderName, long senderId, String content, String createdAt) { }
    public record Assignment(long id, String title, String description, String fileName, long fileSize,
                             String teacherName, String createdAt) { }
    public record MessageRequest(String content) { }
    public record QuizChoice(long id, String content) { }
    public record QuizQuestion(long id, String content, List<QuizChoice> choices) { }
    public record QuizAnswer(long questionId, long choiceId) { }
    public record QuizSubmission(List<QuizAnswer> answers) { }
    public record QuizResult(int score, int maxScore) { }
    private record AttemptInfo(long id, String state) { }
    private record AssignmentFile(String name, String contentType, byte[] bytes) { }
}
