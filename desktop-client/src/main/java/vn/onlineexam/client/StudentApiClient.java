package vn.onlineexam.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class StudentApiClient {
    private static final Gson GSON = new Gson();
    private static final Type CLASS_LIST = new TypeToken<List<Classroom>>() { }.getType();
    private static final Type EXAM_LIST = new TypeToken<List<StudentExam>>() { }.getType();
    private static final Type RESULT_LIST = new TypeToken<List<Result>>() { }.getType();
    private static final Type MESSAGE_LIST = new TypeToken<List<ChatMessage>>() { }.getType();
    private static final Type ASSIGNMENT_LIST = new TypeToken<List<Assignment>>() { }.getType();
    private static final Type QUIZ_QUESTIONS = new TypeToken<List<QuizQuestion>>() { }.getType();
    private final String baseUrl;
    private final String token;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public StudentApiClient(String baseUrl, String token) { this.baseUrl = baseUrl; this.token = token; }

    public CompletableFuture<Summary> summary() { return get("/summary", Summary.class); }
    public CompletableFuture<List<Classroom>> classes() { return get("/classes", CLASS_LIST); }
    public CompletableFuture<List<StudentExam>> exams() { return get("/exams", EXAM_LIST); }
    public CompletableFuture<List<QuizQuestion>> quizQuestions(long sessionId) {
        return get("/exams/" + sessionId + "/questions", QUIZ_QUESTIONS);
    }
    public CompletableFuture<QuizResult> submitQuiz(long sessionId, List<QuizAnswer> answers) {
        return post("/exams/" + sessionId + "/submit", GSON.toJson(new QuizSubmission(answers)), QuizResult.class);
    }
    public CompletableFuture<List<Result>> results() { return get("/results", RESULT_LIST); }
    public CompletableFuture<List<ChatMessage>> messages(long classId) { return get("/classes/" + classId + "/messages", MESSAGE_LIST); }
    public ClassChatSocket openChat(long classId, java.util.function.Consumer<ClassChatSocket.ChatMessage> onMessage,
                                    java.util.function.Consumer<Throwable> onError) {
        ClassChatSocket socket = new ClassChatSocket(baseUrl, token, classId, onMessage, onError);
        socket.connect();
        return socket;
    }
    public CompletableFuture<ChatMessage> sendMessage(long classId, String content) {
        return post("/classes/" + classId + "/messages", GSON.toJson(new MessageRequest(content)), ChatMessage.class);
    }
    public CompletableFuture<List<Assignment>> assignments(long classId) {
        return get("/classes/" + classId + "/assignments", ASSIGNMENT_LIST);
    }
    public CompletableFuture<byte[]> downloadAssignment(long classId, long assignmentId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/student/classes/" + classId
                        + "/assignments/" + assignmentId + "/file"))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ApiException(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            return response.body();
        });
    }

    public void logout() { request("/api/auth/logout", "POST", "").exceptionally(error -> null); }

    private <T> CompletableFuture<T> get(String path, Type type) {
        return request("/api/student" + path, "GET", "").thenApply(body -> GSON.fromJson(body, type));
    }
    private <T> CompletableFuture<T> post(String path, String body, Class<T> type) {
        return request("/api/student" + path, "POST", body).thenApply(response -> GSON.fromJson(response, type));
    }
    private CompletableFuture<String> request(String path, String method, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json; charset=UTF-8");
        HttpRequest request = method.equals("GET") ? builder.GET().build()
                : builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ApiException(response.statusCode(), response.body());
            return response.body();
        });
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
    public record QuizChoice(long id, String content) { }
    public record QuizQuestion(long id, String content, List<QuizChoice> choices) { }
    public record QuizAnswer(long questionId, long choiceId) { }
    public record QuizResult(int score, int maxScore) { }
    private record QuizSubmission(List<QuizAnswer> answers) { }
    private record MessageRequest(String content) { }
    public static final class ApiException extends RuntimeException {
        private final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
    }
}
