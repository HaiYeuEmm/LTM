package vn.onlineexam.server.teacher;

import java.sql.Statement;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
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
import vn.onlineexam.server.auth.TeacherAuthInterceptor;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public TeacherController(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @GetMapping("/summary")
    public Summary summary(HttpServletRequest request) {
        long teacherId = teacherId(request);
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM classrooms WHERE owner_teacher_id = ? AND status = 'ACTIVE') AS classes,
                       (SELECT COUNT(*) FROM class_memberships m JOIN classrooms c ON c.id=m.classroom_id
                        WHERE c.owner_teacher_id = ? AND c.status='ACTIVE' AND m.status='ACTIVE') AS students,
                       (SELECT COUNT(*) FROM exams WHERE owner_teacher_id = ?) AS exams
                """, (rs, row) -> new Summary(rs.getInt("classes"), rs.getInt("students"), rs.getInt("exams")),
                teacherId, teacherId, teacherId);
    }

    @GetMapping("/classes")
    public List<Classroom> classes(HttpServletRequest request) {
        return jdbc.query("""
                SELECT c.id, c.name, c.description, c.class_code, c.status, c.created_at,
                       COUNT(m.id) AS student_count
                FROM classrooms c LEFT JOIN class_memberships m
                  ON m.classroom_id=c.id AND m.status='ACTIVE'
                WHERE c.owner_teacher_id=? GROUP BY c.id ORDER BY c.created_at DESC
                """, (rs, row) -> new Classroom(rs.getLong("id"), rs.getString("name"),
                rs.getString("description"), rs.getString("class_code"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant().toString(), rs.getInt("student_count")), teacherId(request));
    }

    @PostMapping("/classes")
    public Classroom createClass(@RequestBody ClassRequest input, HttpServletRequest request) {
        String name = input.name() == null ? "" : input.name().trim();
        String description = input.description() == null ? null : input.description().trim();
        if (name.isEmpty() || name.length() > 120 || (description != null && description.length() > 500)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên lớp bắt buộc (tối đa 120 ký tự); mô tả tối đa 500 ký tự.");
        }
        long teacherId = teacherId(request);
        String code = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO classrooms(owner_teacher_id,name,description,class_code) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, teacherId); statement.setString(2, name); statement.setString(3, description); statement.setString(4, code);
            return statement;
        }, key);
        return new Classroom(key.getKey().longValue(), name, description, code, "ACTIVE", java.time.Instant.now().toString(), 0);
    }

    @GetMapping("/classes/{classId}/members")
    public List<Member> members(@PathVariable long classId, HttpServletRequest request) {
        requireOwnedClass(classId, teacherId(request));
        return jdbc.query("""
                SELECT u.id,u.full_name,u.email,m.status,m.joined_at
                FROM class_memberships m JOIN users u ON u.id=m.student_id
                WHERE m.classroom_id=? ORDER BY m.status,m.joined_at DESC
                """, (rs, row) -> new Member(rs.getLong("id"), rs.getString("full_name"),
                rs.getString("email"), rs.getString("status"), rs.getTimestamp("joined_at").toInstant().toString()), classId);
    }

    @PostMapping("/classes/{classId}/members")
    public Member addStudent(@PathVariable long classId, @RequestBody AddMemberRequest input, HttpServletRequest request) {
        long teacherId = teacherId(request);
        requireOwnedClass(classId, teacherId);
        String email = input.email() == null ? "" : input.email().trim().toLowerCase(java.util.Locale.ROOT);
        List<Student> matches = jdbc.query("SELECT id,full_name,email FROM users WHERE email=? AND role='STUDENT' AND status='ACTIVE'",
                (rs, row) -> new Student(rs.getLong("id"), rs.getString("full_name"), rs.getString("email")), email);
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy học sinh đang hoạt động với email này.");
        Student student = matches.getFirst();
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO class_memberships(classroom_id,student_id,status,joined_at,removed_at)
                    VALUES(?,?,'ACTIVE',CURRENT_TIMESTAMP(3),NULL)
                    ON DUPLICATE KEY UPDATE status='ACTIVE',joined_at=CURRENT_TIMESTAMP(3),removed_at=NULL
                    """, classId, student.id());
            String className = jdbc.queryForObject("SELECT name FROM classrooms WHERE id=?", String.class, classId);
            jdbc.update("INSERT INTO chat_rooms(classroom_id,created_by,name) VALUES(?,?,?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)",
                    classId, teacherId, className + " · Chat lớp");
            Long roomId = jdbc.queryForObject("SELECT id FROM chat_rooms WHERE classroom_id=?", Long.class, classId);
            upsertChatMember(roomId, teacherId, "TEACHER");
            upsertChatMember(roomId, student.id(), "STUDENT");
        });
        return new Member(student.id(), student.name(), student.email(), "ACTIVE", java.time.Instant.now().toString());
    }

    @GetMapping("/exams")
    public List<Exam> exams(HttpServletRequest request) {
        return jdbc.query("""
                SELECT e.id,e.title,e.description,e.status,e.created_at,
                       (SELECT c.name FROM exam_sessions es JOIN classrooms c ON c.id=es.classroom_id
                        WHERE es.exam_id=e.id ORDER BY es.id DESC LIMIT 1) AS classroom_name
                FROM exams e WHERE e.owner_teacher_id=? ORDER BY e.created_at DESC
                """,
                (rs, row) -> new Exam(rs.getLong("id"), rs.getString("title"), rs.getString("description"),
                        rs.getString("status"), rs.getTimestamp("created_at").toInstant().toString(), rs.getString("classroom_name")), teacherId(request));
    }

    @PostMapping("/exams")
    public Exam createExam(@RequestBody ExamRequest input, HttpServletRequest request) {
        String title = input.title() == null ? "" : input.title().trim();
        String description = input.description() == null ? null : input.description().trim();
        if (title.isEmpty() || title.length() > 180 || (description != null && description.length() > 1000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề bắt buộc (tối đa 180 ký tự); mô tả tối đa 1000 ký tự.");
        }
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO exams(owner_teacher_id,title,description) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, teacherId(request)); statement.setString(2, title); statement.setString(3, description); return statement;
        }, key);
        return new Exam(key.getKey().longValue(), title, description, "DRAFT", java.time.Instant.now().toString(), null);
    }

    @PostMapping("/quizzes")
    public Exam createQuiz(@RequestBody QuizRequest input, HttpServletRequest request) {
        long ownerId = teacherId(request);
        String title = input.title() == null ? "" : input.title().trim();
        String description = input.description() == null ? null : input.description().trim();
        if (title.isBlank() || title.length() > 180 || input.classroomId() < 1
                || input.durationMinutes() < 1 || input.durationMinutes() > 600
                || input.questions() == null || input.questions().isEmpty() || input.questions().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần tiêu đề, lớp, thời lượng hợp lệ và từ 1 đến 100 câu hỏi.");
        }
        requireOwnedClass(input.classroomId(), ownerId);
        for (QuizQuestionInput question : input.questions()) {
            if (question.content() == null || question.content().isBlank() || question.content().length() > 4000
                    || question.choices() == null || question.choices().size() < 2 || question.choices().size() > 6
                    || question.choices().stream().filter(choice -> choice.correct()).count() != 1
                    || question.choices().stream().anyMatch(choice -> choice.content() == null
                        || choice.content().isBlank() || choice.content().length() > 1000)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Mỗi câu hỏi cần 2-6 lựa chọn, nội dung hợp lệ và đúng chính xác một đáp án.");
            }
        }

        final long[] examId = new long[1];
        transactions.executeWithoutResult(status -> {
            KeyHolder bankKey = new GeneratedKeyHolder();
            String bankName = title.length() > 160 ? title.substring(0, 160) : title;
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO question_banks(owner_teacher_id,name,description) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, ownerId); statement.setString(2, bankName); statement.setString(3, description);
                return statement;
            }, bankKey);
            long bankId = bankKey.getKey().longValue();

            KeyHolder examKey = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO exams(owner_teacher_id,title,description,status) VALUES(?,?,?,'PUBLISHED')", Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, ownerId); statement.setString(2, title); statement.setString(3, description);
                return statement;
            }, examKey);
            examId[0] = examKey.getKey().longValue();

            int position = 1;
            for (QuizQuestionInput question : input.questions()) {
                KeyHolder questionKey = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    var statement = connection.prepareStatement(
                            "INSERT INTO questions(question_bank_id,question_type,content,default_points) VALUES(?,'SINGLE_CHOICE',?,1.00)",
                            Statement.RETURN_GENERATED_KEYS);
                    statement.setLong(1, bankId); statement.setString(2, question.content().trim());
                    return statement;
                }, questionKey);
                long questionId = questionKey.getKey().longValue();
                jdbc.update("INSERT INTO exam_questions(exam_id,question_id,position,points) VALUES(?,?,?,1.00)",
                        examId[0], questionId, position++);
                int choicePosition = 1;
                for (QuizChoiceInput choice : question.choices()) {
                    jdbc.update("INSERT INTO question_choices(question_id,content,position,is_correct) VALUES(?,?,?,?)",
                            questionId, choice.content().trim(), choicePosition++, choice.correct());
                }
            }
            jdbc.update("""
                    INSERT INTO exam_sessions(exam_id,classroom_id,opens_at,closes_at,duration_minutes,max_attempts,status)
                    VALUES(?,?,CURRENT_TIMESTAMP(3),DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 365 DAY),?,1,'OPEN')
                    """, examId[0], input.classroomId(), input.durationMinutes());
        });
        String classroomName = jdbc.queryForObject("SELECT name FROM classrooms WHERE id=?", String.class, input.classroomId());
        return new Exam(examId[0], title, description, "PUBLISHED", java.time.Instant.now().toString(), classroomName);
    }

    private void upsertChatMember(long roomId, long userId, String role) {
        jdbc.update("""
                INSERT INTO chat_room_members(room_id,user_id,member_role,status,joined_at,left_at)
                VALUES(?,?,?,'ACTIVE',CURRENT_TIMESTAMP(3),NULL)
                ON DUPLICATE KEY UPDATE status='ACTIVE',joined_at=CURRENT_TIMESTAMP(3),left_at=NULL
                """, roomId, userId, role);
    }

    private void requireOwnedClass(long classId, long teacherId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM classrooms WHERE id=? AND owner_teacher_id=?", Integer.class, classId, teacherId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học.");
    }

    private static long teacherId(HttpServletRequest request) { return (long) request.getAttribute(TeacherAuthInterceptor.USER_ID_ATTRIBUTE); }
    public record Summary(int classes, int students, int exams) { }
    public record Classroom(long id, String name, String description, String classCode, String status, String createdAt, int studentCount) { }
    public record Member(long id, String fullName, String email, String status, String joinedAt) { }
    public record Exam(long id, String title, String description, String status, String createdAt, String classroomName) { }
    public record ClassRequest(String name, String description) { }
    public record AddMemberRequest(String email) { }
    public record ExamRequest(String title, String description) { }
    public record QuizRequest(long classroomId, String title, String description, int durationMinutes,
                              List<QuizQuestionInput> questions) { }
    public record QuizQuestionInput(String content, List<QuizChoiceInput> choices) { }
    public record QuizChoiceInput(String content, boolean correct) { }
    private record Student(long id, String name, String email) { }
}
