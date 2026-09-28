package vn.onlineexam.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class AuthApplication extends Application {
    private static final String EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final String apiBaseUrl = normalizeApiBaseUrl(System.getProperty(
            "onlineexam.api.url",
            System.getenv().getOrDefault("ONLINE_EXAM_API_URL", "http://192.168.1.14:8080")));
    private boolean registering;
    private VBox formFields;
    private Label heading;
    private Label description;
    private Label message;
    private Button submitButton;
    private Button modeButton;
    private HBox optionsRow;
    private TextField nameField;
    private TextField emailField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private CheckBox termsCheckBox;
    private Stage primaryStage;
    private Scene authScene;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        StackPane root = new StackPane();
        root.getStyleClass().add("app-background");

        HBox shell = new HBox();
        shell.getStyleClass().add("app-shell");
        shell.setMaxWidth(1120);
        shell.setMaxHeight(700);

        VBox brandPanel = buildBrandPanel();
        VBox authPanel = buildAuthPanel();
        HBox.setHgrow(authPanel, Priority.ALWAYS);
        shell.getChildren().addAll(brandPanel, authPanel);
        root.getChildren().add(shell);

        authScene = new Scene(root, 1120, 740);
        authScene.getStylesheets().add(getClass().getResource("/styles/auth.css").toExternalForm());
        stage.setTitle("Examly | Tài khoản của bạn");
        stage.setMinWidth(900);
        stage.setMinHeight(660);
        stage.setScene(authScene);
        stage.show();
    }

    private VBox buildBrandPanel() {
        VBox panel = new VBox(0);
        panel.getStyleClass().add("brand-panel");
        panel.setPrefWidth(460);
        panel.setMinWidth(390);
        panel.setPadding(new Insets(42, 42, 38, 48));

        HBox brand = new HBox(12);
        brand.setAlignment(Pos.CENTER_LEFT);
        Label logo = new Label("E");
        logo.getStyleClass().add("brand-mark");
        Label brandName = new Label("examly");
        brandName.getStyleClass().add("brand-name");
        brand.getChildren().addAll(logo, brandName);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label eyebrow = new Label("NỀN TẢNG THI TRỰC TUYẾN");
        eyebrow.getStyleClass().add("brand-eyebrow");
        Label headline = new Label("Tập trung làm bài.\nTự tin thể hiện.");
        headline.getStyleClass().add("brand-headline");
        headline.setWrapText(true);
        Label intro = new Label("Không gian thi trực tuyến rõ ràng, an toàn và dễ sử dụng cho cả giáo viên lẫn học sinh.");
        intro.getStyleClass().add("brand-copy");
        intro.setWrapText(true);

        VBox features = new VBox(16,
                feature("01", "Bài thi được tổ chức khoa học"),
                feature("02", "Tự động lưu tiến trình làm bài"),
                feature("03", "Kết nối lớp học trong thời gian thực"));
        features.getStyleClass().add("feature-list");

        Region bottomSpacer = new Region();
        VBox.setVgrow(bottomSpacer, Priority.ALWAYS);
        Label footer = new Label("© 2026 Examly  ·  Học tập theo cách của bạn");
        footer.getStyleClass().add("brand-footer");
        panel.getChildren().addAll(brand, spacer, eyebrow, headline, intro, features, bottomSpacer, footer);
        return panel;
    }

    private HBox feature(String number, String text) {
        Label badge = new Label(number);
        badge.getStyleClass().add("feature-number");
        Label copy = new Label(text);
        copy.getStyleClass().add("feature-copy");
        HBox row = new HBox(13, badge, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox buildAuthPanel() {
        VBox panel = new VBox();
        panel.getStyleClass().add("auth-panel");
        panel.setPadding(new Insets(40, 68, 40, 68));
        panel.setAlignment(Pos.CENTER);

        VBox content = new VBox(0);
        content.setMaxWidth(390);

        HBox topLine = new HBox();
        topLine.setAlignment(Pos.CENTER_RIGHT);
        Label prompt = new Label("Chưa có tài khoản?");
        prompt.getStyleClass().add("top-prompt");
        modeButton = new Button("Đăng ký");
        modeButton.getStyleClass().add("text-button");
        modeButton.setOnAction(event -> toggleMode());
        topLine.getChildren().addAll(prompt, modeButton);

        heading = new Label();
        heading.getStyleClass().add("form-heading");
        description = new Label();
        description.getStyleClass().add("form-description");

        formFields = new VBox(15);
        nameField = new TextField();
        nameField.setPromptText("Nguyễn Văn An");
        emailField = new TextField();
        emailField.setPromptText("ban@example.com");
        passwordField = new PasswordField();
        passwordField.setPromptText("Nhập mật khẩu");
        confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Nhập lại mật khẩu");
        termsCheckBox = new CheckBox("Tôi đồng ý với điều khoản sử dụng và chính sách bảo mật");
        termsCheckBox.getStyleClass().add("terms-check");

        formFields.getChildren().addAll(
                field("Họ và tên", nameField),
                field("Email", emailField),
                field("Mật khẩu", passwordField),
                field("Xác nhận mật khẩu", confirmPasswordField),
                termsCheckBox);

        optionsRow = new HBox();
        optionsRow.setAlignment(Pos.CENTER_LEFT);
        CheckBox remember = new CheckBox("Ghi nhớ đăng nhập");
        remember.getStyleClass().add("terms-check");
        Region optionsSpacer = new Region();
        HBox.setHgrow(optionsSpacer, Priority.ALWAYS);
        Button forgot = new Button("Quên mật khẩu?");
        forgot.getStyleClass().add("text-button");
        forgot.setOnAction(event -> showMessage("Vui lòng liên hệ quản trị viên để đặt lại mật khẩu."));
        optionsRow.getChildren().addAll(remember, optionsSpacer, forgot);

        submitButton = new Button();
        submitButton.getStyleClass().add("primary-button");
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.setOnAction(event -> submit());

        message = new Label();
        message.getStyleClass().add("form-message");
        message.setWrapText(true);
        message.setMinHeight(24);

        HBox divider = new HBox(12);
        divider.setAlignment(Pos.CENTER);
        Region leftLine = new Region();
        leftLine.getStyleClass().add("divider-line");
        Region rightLine = new Region();
        rightLine.getStyleClass().add("divider-line");
        HBox.setHgrow(leftLine, Priority.ALWAYS);
        HBox.setHgrow(rightLine, Priority.ALWAYS);
        Label dividerText = new Label("HOẶC TIẾP TỤC VỚI");
        dividerText.getStyleClass().add("divider-text");
        divider.getChildren().addAll(leftLine, dividerText, rightLine);

        Button googleButton = new Button("G  Tiếp tục với Google");
        googleButton.getStyleClass().add("secondary-button");
        googleButton.setMaxWidth(Double.MAX_VALUE);
        googleButton.setOnAction(event -> showMessage("Đăng nhập Google sẽ được kết nối sau."));

        content.getChildren().addAll(topLine, heading, description, formFields, optionsRow, submitButton, message, divider, googleButton);
        VBox.setMargin(heading, new Insets(34, 0, 8, 0));
        VBox.setMargin(description, new Insets(0, 0, 25, 0));
        VBox.setMargin(optionsRow, new Insets(18, 0, 22, 0));
        VBox.setMargin(submitButton, new Insets(0, 0, 5, 0));
        VBox.setMargin(divider, new Insets(18, 0, 16, 0));
        VBox.setMargin(googleButton, new Insets(0, 0, 0, 0));

        panel.getChildren().add(content);
        updateMode();
        return panel;
    }

    private VBox field(String label, TextField input) {
        Label caption = new Label(label);
        caption.getStyleClass().add("field-label");
        input.getStyleClass().add("form-input");
        input.setPrefHeight(48);
        VBox wrapper = new VBox(7, caption, input);
        return wrapper;
    }

    private void toggleMode() {
        registering = !registering;
        message.setText("");
        updateMode();
    }

    private void updateMode() {
        heading.setText(registering ? "Tạo tài khoản" : "Chào mừng trở lại");
        description.setText(registering
                ? "Đăng ký để bắt đầu sử dụng Examly."
                : "Đăng nhập để tiếp tục với lớp học của bạn.");
        modeButton.setText(registering ? "Đăng nhập" : "Đăng ký");
        submitButton.setText(registering ? "Tạo tài khoản" : "Đăng nhập");
        nameField.setManaged(registering);
        nameField.setVisible(registering);
        confirmPasswordField.setManaged(registering);
        confirmPasswordField.setVisible(registering);
        termsCheckBox.setManaged(registering);
        termsCheckBox.setVisible(registering);
        optionsRow.setManaged(!registering);
        optionsRow.setVisible(!registering);
    }

    private void submit() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        if (registering) {
            String fullName = nameField.getText().trim();
            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPasswordField.getText().isEmpty()) {
                showMessage("Vui lòng điền đầy đủ thông tin.");
            } else if (fullName.length() > 120) {
                showMessage("Họ tên tối đa 120 ký tự.");
            } else if (!isValidEmail(email)) {
                showMessage("Email chưa đúng định dạng.");
            } else if (!isValidPassword(password)) {
                showMessage("Mật khẩu cần có ít nhất 8 ký tự và tối đa 72 byte UTF-8.");
            } else if (!password.equals(confirmPasswordField.getText())) {
                showMessage("Mật khẩu xác nhận chưa khớp.");
            } else if (!termsCheckBox.isSelected()) {
                showMessage("Bạn cần đồng ý với điều khoản để tiếp tục.");
            } else {
                sendAuthRequest("/register", formBody(
                        "fullName", fullName,
                        "email", email,
                        "password", password,
                        "termsAccepted", "true"), true, email);
            }
        } else if (email.isEmpty() || password.isEmpty()) {
            showMessage("Vui lòng nhập email và mật khẩu.");
        } else if (!isValidEmail(email)) {
            showMessage("Email chưa đúng định dạng.");
        } else {
            sendAuthRequest("/login", formBody("email", email, "password", password), false, email);
        }
    }

    private void sendAuthRequest(String path, String body, boolean registration, String email) {
        submitButton.setDisable(true);
        modeButton.setDisable(true);
        submitButton.setText("Đang xử lý...");
        message.setText("");

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBaseUrl + "/api/auth" + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    submitButton.setDisable(false);
                    modeButton.setDisable(false);
                    submitButton.setText(registering ? "Tạo tài khoản" : "Đăng nhập");

                    if (error != null) {
                        showMessage("Không kết nối được server. Hãy kiểm tra server đang chạy tại " + apiBaseUrl + ".");
                        return;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        showMessage(response.body().isBlank()
                                ? "Yêu cầu thất bại. Vui lòng thử lại."
                                : response.body());
                        return;
                    }

                    if (registration) {
                        toggleMode();
                        emailField.setText(email);
                        passwordField.clear();
                        confirmPasswordField.clear();
                        showMessage(response.body());
                    } else {
                        if ("TEACHER".equalsIgnoreCase(response.body().trim())) {
                            String token = response.headers().firstValue("X-Auth-Token").orElse("");
                            String fullName = response.headers().firstValue("X-User-Name").orElse(email);
                            showTeacherDashboard(email, fullName, token);
                        } else if ("STUDENT".equalsIgnoreCase(response.body().trim())) {
                            String token = response.headers().firstValue("X-Auth-Token").orElse("");
                            String fullName = response.headers().firstValue("X-User-Name").orElse(email);
                            showStudentDashboard(email, fullName, token);
                        } else {
                            showWelcomeScreen(email);
                        }
                    }
                }));
    }

    private void showTeacherDashboard(String email, String fullName, String token) {
        TeacherApiClient api = new TeacherApiClient(apiBaseUrl, token);
        TeacherDashboard dashboard = new TeacherDashboard(email, fullName, api, () -> {
            api.logout();
            passwordField.clear();
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(660);
            primaryStage.setTitle("Examly | Tài khoản của bạn");
            primaryStage.setScene(authScene);
        });
        Scene scene = new Scene(dashboard, 1280, 820);
        scene.getStylesheets().add(getClass().getResource("/styles/auth.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/styles/teacher.css").toExternalForm());
        primaryStage.setMinWidth(1120);
        primaryStage.setMinHeight(720);
        primaryStage.setTitle("Examly | Không gian giáo viên");
        primaryStage.setScene(scene);
    }

    private void showStudentDashboard(String email, String fullName, String token) {
        StudentApiClient api = new StudentApiClient(apiBaseUrl, token);
        StudentDashboard dashboard = new StudentDashboard(email, fullName, api, () -> {
            api.logout();
            passwordField.clear();
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(660);
            primaryStage.setTitle("Examly | Tài khoản của bạn");
            primaryStage.setScene(authScene);
        });
        Scene scene = new Scene(dashboard, 1280, 820);
        scene.getStylesheets().add(getClass().getResource("/styles/auth.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/styles/student.css").toExternalForm());
        primaryStage.setMinWidth(1080);
        primaryStage.setMinHeight(700);
        primaryStage.setTitle("Examly | Không gian học sinh");
        primaryStage.setScene(scene);
    }

    private void showWelcomeScreen(String email) {
        Label brand = new Label("examly");
        brand.getStyleClass().add("brand-name");

        Label heading = new Label("Đăng nhập thành công");
        heading.getStyleClass().add("welcome-heading");
        Label account = new Label(email);
        account.getStyleClass().add("welcome-email");
        Label description = new Label("Chào mừng bạn đến với hệ thống thi trực tuyến.");
        description.getStyleClass().add("form-description");

        Button logout = new Button("Đăng xuất");
        logout.getStyleClass().add("primary-button");
        logout.setOnAction(event -> {
            passwordField.clear();
            primaryStage.setScene(authScene);
        });

        VBox card = new VBox(18, brand, heading, account, description, logout);
        card.getStyleClass().add("welcome-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(480);
        card.setPadding(new Insets(48));

        StackPane root = new StackPane(card);
        root.getStyleClass().add("app-background");
        Scene scene = new Scene(root, 900, 620);
        scene.getStylesheets().add(getClass().getResource("/styles/auth.css").toExternalForm());
        primaryStage.setScene(scene);
    }

    private static boolean isValidEmail(String email) {
        return email.length() <= 254 && email.matches(EMAIL_REGEX);
    }

    private static boolean isValidPassword(String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        return password.length() >= 8 && bytes <= 72;
    }

    private static String formBody(String... values) {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < values.length; index += 2) {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(URLEncoder.encode(values[index], StandardCharsets.UTF_8));
            body.append('=');
            body.append(URLEncoder.encode(values[index + 1], StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private static String normalizeApiBaseUrl(String url) {
        String normalized = url == null || url.isBlank() ? "http://localhost:8080" : url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void showMessage(String text) {
        message.setText(text);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
