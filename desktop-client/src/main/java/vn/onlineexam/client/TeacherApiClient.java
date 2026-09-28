package vn.onlineexam.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class TeacherApiClient {
    private static final Gson GSON = new Gson();
    private static final Type CLASS_LIST = new TypeToken<List<Classroom>>() { }.getType();
    private static final Type EXAM_LIST = new TypeToken<List<Exam>>() { }.getType();
    private static final Type MEMBER_LIST = new TypeToken<List<Member>>() { }.getType();
    private static final Type MESSAGE_LIST = new TypeToken<List<ChatMessage>>() { }.getType();
    private static final Type ASSIGNMENT_LIST = new TypeToken<List<Assignment>>() { }.getType();
    private final String baseUrl;
    private final String token;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public TeacherApiClient(String baseUrl, String token) { this.baseUrl = baseUrl; this.token = token; }

    public CompletableFuture<List<Classroom>> classes() { return get("/classes", CLASS_LIST); }
    public CompletableFuture<Summary> summary() { return get("/summary", Summary.class); }
    public CompletableFuture<List<Exam>> exams() { return get("/exams", EXAM_LIST); }
    public CompletableFuture<List<Member>> members(long classId) { return get("/classes/" + classId + "/members", MEMBER_LIST); }
    public CompletableFuture<Classroom> createClass(String name, String description) {
        return post("/classes", new ClassRequest(name, description), Classroom.class);
    }
    public CompletableFuture<Member> addStudent(long classId, String email) {
        return post("/classes/" + classId + "/members", new AddMemberRequest(email), Member.class);
    }
    public CompletableFuture<List<Member>> addStudents(long classId, List<String> emails) {
        return post("/classes/" + classId + "/members/bulk", new AddMembersRequest(emails), MEMBER_LIST);
    }
    public CompletableFuture<List<ChatMessage>> messages(long classId) { return get("/classes/" + classId + "/messages", MESSAGE_LIST); }
    public ClassChatSocket openChat(long classId, java.util.function.Consumer<ClassChatSocket.ChatMessage> onMessage,
                                    java.util.function.Consumer<Throwable> onError) {
        ClassChatSocket socket = new ClassChatSocket(baseUrl, token, classId, onMessage, onError);
        socket.connect();
        return socket;
    }
    public CompletableFuture<ChatMessage> sendMessage(long classId, String content) {
        return post("/classes/" + classId + "/messages", new MessageRequest(content), ChatMessage.class);
    }
    public CompletableFuture<List<Assignment>> assignments(long classId) {
        return get("/classes/" + classId + "/assignments", ASSIGNMENT_LIST);
    }
    public CompletableFuture<Assignment> uploadAssignment(long classId, String title, String description, Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String boundary = "----Examly" + java.util.UUID.randomUUID().toString().replace("-", "");
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                writeField(body, boundary, "title", title);
                writeField(body, boundary, "description", description == null ? "" : description);
                String fileName = path.getFileName().toString().replace("\\", "_").replace("\"", "_");
                String mime = Files.probeContentType(path);
                if (mime == null) mime = "application/octet-stream";
                body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                        + "\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                body.write(Files.readAllBytes(path));
                body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/teacher/classes/" + classId + "/assignments"))
                        .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + token)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ApiException(response.statusCode(), response.body());
                return GSON.fromJson(response.body(), Assignment.class);
            } catch (Exception exception) {
                if (exception instanceof RuntimeException runtime) throw runtime;
                throw new java.util.concurrent.CompletionException(exception);
            }
        });
    }
    public CompletableFuture<byte[]> downloadAssignment(long classId, long assignmentId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/teacher/classes/" + classId
                        + "/assignments/" + assignmentId + "/file"))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ApiException(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            return response.body();
        });
    }
    public CompletableFuture<Void> deleteAssignment(long classId, long assignmentId) {
        return request("/api/teacher/classes/" + classId + "/assignments/" + assignmentId, "DELETE", "")
                .thenApply(response -> null);
    }
    public CompletableFuture<Exam> createExam(String title, String description) {
        return post("/exams", new ExamRequest(title, description), Exam.class);
    }
    public CompletableFuture<Exam> createQuiz(long classroomId, String title, String description,
            int durationMinutes, List<QuizQuestionInput> questions) {
        return post("/quizzes", new QuizRequest(classroomId, title, description, durationMinutes, questions), Exam.class);
    }

    public void logout() {
        request("/api/auth/logout", "POST", "").exceptionally(error -> null);
    }

    private <T> CompletableFuture<T> get(String path, Type type) {
        return request("/api/teacher" + path, "GET", "").thenApply(response -> GSON.fromJson(response, type));
    }
    private <T> CompletableFuture<T> post(String path, Object body, Class<T> type) {
        return request("/api/teacher" + path, "POST", GSON.toJson(body)).thenApply(response -> GSON.fromJson(response, type));
    }
    private <T> CompletableFuture<T> post(String path, Object body, Type type) {
        return request("/api/teacher" + path, "POST", GSON.toJson(body)).thenApply(response -> GSON.fromJson(response, type));
    }
    private static void writeField(ByteArrayOutputStream body, String boundary, String name, String value) throws java.io.IOException {
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value
                + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
    private CompletableFuture<String> request(String path, String method, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json; charset=UTF-8");
        HttpRequest request = method.equals("GET") ? builder.GET().build()
                : builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(response.statusCode(), response.body());
            }
            return response.body();
        });
    }

    public record Summary(int classes, int students, int exams) { }
    public record Classroom(long id, String name, String description, String classCode, String status, String createdAt, int studentCount) { }
    public record Exam(long id, String title, String description, String status, String createdAt, String classroomName) { }
    public record Member(long id, String fullName, String email, String status, String joinedAt) { }
    public record ChatMessage(long id, String senderName, long senderId, String content, String createdAt) { }
    public record Assignment(long id, String title, String description, String fileName, long fileSize, String teacherName, String createdAt) { }
    private record ClassRequest(String name, String description) { }
    private record AddMemberRequest(String email) { }
    private record AddMembersRequest(List<String> emails) { }
    private record MessageRequest(String content) { }
    private record ExamRequest(String title, String description) { }
    public record QuizQuestionInput(String content, List<QuizChoiceInput> choices) { }
    public record QuizChoiceInput(String content, boolean correct) { }
    private record QuizRequest(long classroomId, String title, String description, int durationMinutes,
                               List<QuizQuestionInput> questions) { }
    public static final class ApiException extends RuntimeException {
        private final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
    }
}
