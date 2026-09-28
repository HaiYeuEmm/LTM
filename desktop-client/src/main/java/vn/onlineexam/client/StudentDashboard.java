package vn.onlineexam.client;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public final class StudentDashboard extends BorderPane {
    private final String email;
    private final String fullName;
    private final StudentApiClient api;
    private final Runnable onLogout;
    private final VBox content = new VBox(18);
    private Button selectedNav;
    private Button classesNav;
    private javafx.animation.Timeline chatRefresh;
    private ClassChatSocket chatSocket;
    private javafx.animation.Timeline liveSync;
    private javafx.animation.Timeline assignmentSync;
    private javafx.animation.Timeline noticeTimer;
    private VBox chatMessages;
    private final Label liveNotice = styled("", "student-live-notice");
    private final Map<Long, StudentApiClient.Classroom> knownClasses = new HashMap<>();
    private boolean liveSyncPrimed;
    private String lastSyncError;
    private String currentPage = "overview";
    private Label classMetricValue;
    private Label examMetricValue;
    private Label resultMetricValue;
    private List<StudentApiClient.Assignment> visibleAssignments;

    public StudentDashboard(String email, String fullName, StudentApiClient api, Runnable onLogout) {
        this.email = email;
        this.fullName = fullName;
        this.api = api;
        this.onLogout = onLogout;
        getStyleClass().add("student-root");
        setLeft(buildSidebar());
        liveNotice.setManaged(false);
        liveNotice.setVisible(false);
        setTop(liveNotice);
        content.getStyleClass().add("student-page-content");
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("student-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(scroll);
        showOverview();
        startLiveSync();
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(11);
        sidebar.getStyleClass().add("student-sidebar");
        sidebar.setPadding(new Insets(24, 18, 20, 18));
        sidebar.setPrefWidth(250);
        HBox brand = new HBox(10, styled("E", "student-brand-mark"), styled("examly", "student-brand-name"));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("student-sidebar-brand");
        Button overview = nav("◈   Tổng quan", this::showOverview);
        classesNav = nav("▦   Lớp học", this::showClasses);
        Button exams = nav("✦   Bài kiểm tra", this::showExams);
        Button results = nav("◷   Kết quả", this::showResults);
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        HBox profile = new HBox(10, styled(initials(fullName), "student-avatar"),
                new VBox(4, styled(fullName, "student-profile-name"), styled(email, "student-profile-email"),
                        styled("HỌC SINH", "student-profile-role")));
        profile.setAlignment(Pos.CENTER_LEFT);
        profile.getStyleClass().add("student-user-card");
        Button logout = nav("↪   Đăng xuất", () -> { stopLiveSync(); onLogout.run(); });
        sidebar.getChildren().addAll(brand, styled("KHÔNG GIAN HỌC TẬP", "student-side-caption"),
                overview, classesNav, exams, results, spacer, profile, logout);
        setActiveNav(overview);
        return sidebar;
    }

    private Button nav(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("student-nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(event -> { setActiveNav(button); action.run(); });
        return button;
    }

    private void setActiveNav(Button button) {
        if (selectedNav != null) selectedNav.getStyleClass().remove("student-nav-active");
        selectedNav = button;
        if (!button.getStyleClass().contains("student-nav-active")) button.getStyleClass().add("student-nav-active");
    }

    private Button action(String title, Runnable action) {
        Button button = new Button(title);
        button.getStyleClass().add("student-action-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private Button link(String title, Runnable action) {
        Button button = new Button(title);
        button.getStyleClass().add("student-link-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private void showOverview() {
        currentPage = "overview";
        stopChatRefresh();
        classMetricValue = null; examMetricValue = null; resultMetricValue = null;
        content.getChildren().setAll(styled("Đang tải dữ liệu học tập…", "student-muted"));
        api.summary().whenComplete((summary, error) -> Platform.runLater(() -> {
            if (error != null) { content.getChildren().setAll(styled(errorText(error), "student-muted")); return; }
            HBox metrics = new HBox(14,
                    metric("01", "LỚP CỦA BẠN", summary.classes(), "student-metric-blue", 0),
                    metric("02", "BÀI ĐANG MỞ", summary.availableExams(), "student-metric-teal", 1),
                    metric("03", "KẾT QUẢ", summary.completed(), "student-metric-violet", 2));
            for (Node node : metrics.getChildren()) HBox.setHgrow(node, Priority.ALWAYS);
            content.getChildren().setAll(hero(), metrics,
                    section("Truy cập nhanh", "Tiếp tục học tập và theo dõi tiến độ"),
                    new HBox(12, action("Xem lớp học", this::showClasses), action("Bài kiểm tra", this::showExams)),
                    infoCard("Không gian của bạn", "Xem thông báo trong nhóm lớp, nhận tệp bài tập từ giáo viên và theo dõi kết quả các bài đã hoàn thành."));
        }));
    }

    private HBox hero() {
        VBox text = new VBox(8, styled("EXAMLY  ·  KHÔNG GIAN HỌC SINH", "student-hero-eyebrow"),
                styled("Chào " + firstName(fullName) + "!", "student-hero-title"),
                styled("Sẵn sàng cho buổi học tiếp theo của bạn?", "student-hero-copy"));
        HBox banner = new HBox(text, spacer(), styled("✦", "student-hero-emblem"));
        banner.getStyleClass().add("student-hero");
        banner.setAlignment(Pos.CENTER_LEFT);
        return banner;
    }

    private VBox metric(String index, String caption, int value, String accent, int metricType) {
        HBox line = new HBox(styled(index, "student-metric-index"), spacer(), styled("●", "student-metric-dot"));
        Label valueLabel = styled(String.valueOf(value), "student-metric-value");
        if (metricType == 0) classMetricValue = valueLabel;
        else if (metricType == 1) examMetricValue = valueLabel;
        else resultMetricValue = valueLabel;
        VBox card = new VBox(9, line, styled(caption, "student-metric-caption"), valueLabel);
        card.getStyleClass().addAll("student-metric-card", accent);
        return card;
    }

    private HBox section(String title, String description) {
        return new HBox(new VBox(4, styled(title, "student-section-title"), styled(description, "student-muted")));
    }

    private void showClasses() {
        currentPage = "classes";
        stopChatRefresh();
        content.getChildren().setAll(styled("Lớp học của tôi", "student-page-title"),
                styled("Các lớp mà giáo viên đã thêm bạn vào.", "student-muted"), styled("Đang tải lớp học…", "student-muted"));
        Node loading = content.getChildren().get(2);
        api.classes().whenComplete((classes, error) -> Platform.runLater(() -> {
            if (!currentPage.equals("classes")) return;
            if (error != null) { content.getChildren().remove(loading); content.getChildren().add(styled(errorText(error), "student-muted")); return; }
            renderClasses(classes);
        }));
    }

    private void renderClasses(List<StudentApiClient.Classroom> classes) {
        content.getChildren().setAll(styled("Lớp học của tôi", "student-page-title"),
                styled("Các lớp mà giáo viên đã thêm bạn vào. Danh sách tự đồng bộ khi được thêm vào lớp mới.", "student-muted"));
        if (classes.isEmpty()) {
            content.getChildren().add(infoCard("Chưa có lớp học", "Khi giáo viên thêm bạn vào lớp, lớp học sẽ xuất hiện ở đây."));
            return;
        }
        for (StudentApiClient.Classroom classroom : classes) {
            VBox card = new VBox(11);
            card.getStyleClass().add("student-class-card");
            HBox top = new HBox(12, styled("▦", "student-class-icon"),
                    new VBox(5, styled(classroom.name(), "student-card-title"),
                            styled("Mã lớp " + classroom.classCode() + "  ·  GV " + classroom.teacherName(), "student-muted")), spacer(),
                    link("Mở lớp", () -> showClassDetails(classroom)));
            top.setAlignment(Pos.CENTER_LEFT);
            HBox details = new HBox(9, chip(classroom.messageCount() + " tin nhắn"), chip(classroom.assignmentCount() + " bài tập"));
            card.getChildren().addAll(top, details);
            content.getChildren().add(card);
        }
    }

    private void showExams() {
        currentPage = "exams";
        stopChatRefresh();
        content.getChildren().setAll(styled("Bài kiểm tra", "student-page-title"),
                styled("Lịch thi và trạng thái bài làm trong các lớp của bạn.", "student-muted"), styled("Đang tải bài kiểm tra…", "student-muted"));
        Node loading = content.getChildren().get(2);
        api.exams().whenComplete((exams, error) -> Platform.runLater(() -> {
            content.getChildren().remove(loading);
            if (error != null) { content.getChildren().add(styled(errorText(error), "student-muted")); return; }
            if (exams.isEmpty()) { content.getChildren().add(infoCard("Chưa có bài kiểm tra", "Bài thi được giáo viên phát hành cho lớp sẽ xuất hiện tại đây.")); return; }
            for (StudentApiClient.StudentExam exam : exams) {
                String attempt = exam.attemptState() == null ? "Chưa bắt đầu" : studentStatus(exam.attemptState());
                String timing = displayDate(exam.opensAt()) + " – " + displayDate(exam.closesAt());
                VBox card = new VBox(8, styled(exam.title(), "student-card-title"),
                        styled(exam.className() + "  ·  " + exam.durationMinutes() + " phút", "student-muted"),
                        styled("Thời gian: " + timing, "student-muted"), styled("Trạng thái: " + attempt, "student-status-chip"));
                card.getStyleClass().add("student-class-card");
                if (exam.attemptState() == null || "NOT_STARTED".equals(exam.attemptState())
                        || "IN_PROGRESS".equals(exam.attemptState()))
                    card.getChildren().add(action("Bat dau lam quiz", () -> startQuiz(exam)));
                content.getChildren().add(card);
            }
        }));
    }

    private void startQuiz(StudentApiClient.StudentExam exam) {
        api.quizQuestions(exam.sessionId()).whenComplete((questions, error) -> Platform.runLater(() -> {
            if (error != null) { showError(error); return; }
            if (questions.isEmpty()) { info("Quiz trong", "De thi nay chua co cau hoi."); return; }
            VBox form = new VBox(14);
            Map<Long, javafx.scene.control.ToggleGroup> groups = new HashMap<>();
            for (StudentApiClient.QuizQuestion question : questions) {
                VBox card = new VBox(8, styled(question.content(), "student-card-title"));
                javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();
                groups.put(question.id(), group);
                for (StudentApiClient.QuizChoice choice : question.choices()) {
                    javafx.scene.control.RadioButton option = new javafx.scene.control.RadioButton(choice.content());
                    option.setUserData(choice.id()); option.setToggleGroup(group); card.getChildren().add(option);
                }
                card.getStyleClass().add("student-panel"); form.getChildren().add(card);
            }
            ScrollPane scroll = new ScrollPane(form); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(540);
            javafx.scene.control.Dialog<ButtonType> dialog = new javafx.scene.control.Dialog<>();
            dialog.setTitle(exam.title()); dialog.setHeaderText("Chon mot dap an cho moi cau hoi");
            dialog.getDialogPane().setContent(scroll); dialog.getDialogPane().setPrefWidth(700);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> {
                List<StudentApiClient.QuizAnswer> answers = new java.util.ArrayList<>();
                for (var entry : groups.entrySet()) {
                    if (entry.getValue().getSelectedToggle() == null) {
                        info("Chua tra loi het", "Hay chon dap an cho tat ca cau hoi roi nop bai."); return;
                    }
                    long choiceId = (long) entry.getValue().getSelectedToggle().getUserData();
                    answers.add(new StudentApiClient.QuizAnswer(entry.getKey(), choiceId));
                }
                api.submitQuiz(exam.sessionId(), answers).whenComplete((result, submitError) -> Platform.runLater(() -> {
                    if (submitError != null) showError(submitError);
                    else { info("Da nop bai", "Diem cua ban: " + result.score() + " / " + result.maxScore()); showExams(); }
                }));
            });
        }));
    }

    private void showResults() {
        currentPage = "results";
        stopChatRefresh();
        content.getChildren().setAll(styled("Kết quả học tập", "student-page-title"),
                styled("Điểm các bài thi đã được chấm.", "student-muted"), styled("Đang tải kết quả…", "student-muted"));
        Node loading = content.getChildren().get(2);
        api.results().whenComplete((results, error) -> Platform.runLater(() -> {
            content.getChildren().remove(loading);
            if (error != null) { content.getChildren().add(styled(errorText(error), "student-muted")); return; }
            if (results.isEmpty()) { content.getChildren().add(infoCard("Chưa có kết quả", "Kết quả sẽ xuất hiện sau khi giáo viên chấm bài.")); return; }
            for (StudentApiClient.Result result : results) {
                String score = result.score().stripTrailingZeros().toPlainString() + " / " + result.maxScore().stripTrailingZeros().toPlainString();
                content.getChildren().add(scoreCard(result.title(), result.className(), score, displayDate(result.gradedAt())));
            }
        }));
    }

    private void showClassDetails(StudentApiClient.Classroom classroom) {
        currentPage = "class:" + classroom.id();
        stopChatRefresh();
        visibleAssignments = null;
        content.getChildren().clear();
        HBox heading = new HBox(12, new VBox(5, styled(classroom.name(), "student-page-title"),
                styled("Mã lớp " + classroom.classCode() + "  ·  Giáo viên " + classroom.teacherName(), "student-muted")),
                spacer(), link("← Danh sách lớp", this::showClasses));
        heading.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(heading);

        VBox assignmentRows = new VBox(9);
        VBox chatCard = new VBox(11); chatCard.getStyleClass().add("student-panel");
        chatMessages = new VBox(8);
        HBox chatTitle = new HBox(10, styled("Trò chuyện lớp", "student-card-title"), spacer(),
                link("Làm mới", () -> loadMessages(classroom, chatMessages)));
        ScrollPane messageScroll = new ScrollPane(chatMessages); messageScroll.setFitToWidth(true);
        messageScroll.setPrefHeight(250); messageScroll.getStyleClass().add("student-chat-scroll");
        TextField input = new TextField(); input.setPromptText("Nhắn tin với giáo viên và các bạn…");
        Button send = action("Gửi", () -> sendMessage(classroom, input)); input.setOnAction(event -> send.fire());
        HBox composer = new HBox(9, input, send); HBox.setHgrow(input, Priority.ALWAYS);
        chatCard.getChildren().addAll(chatTitle, messageScroll, composer);
        content.getChildren().add(chatCard);
        loadMessages(classroom, chatMessages);
        chatSocket = api.openChat(classroom.id(), message -> Platform.runLater(() -> {
            if (message.assignments() != null) renderAssignmentSnapshot(classroom, assignmentRows, message.assignments());
            else if (chatMessages != null) appendChatMessage(message.senderName(), message.content());
        }), error -> Platform.runLater(() -> {
            if (chatMessages != null) chatMessages.getChildren().setAll(styled("Mất kết nối chat Socket: " + errorText(error), "student-muted"));
        }));

        VBox assignmentsCard = new VBox(10); assignmentsCard.getStyleClass().add("student-panel");
        VBox rows = assignmentRows;
        assignmentsCard.getChildren().addAll(styled("Bài tập và tài liệu", "student-card-title"), rows);
        content.getChildren().add(assignmentsCard);
        rows.getChildren().setAll(styled("Đang kết nối socket để tải bài tập…", "student-muted"));
        // Tạm tắt polling 4 giây để kiểm tra sự kiện ASSIGNMENT_CREATED/DELETED qua TCP socket.
        // Bật lại sau khi kiểm tra bằng cách bỏ comment ba dòng tạo và chạy Timeline bên dưới.
        // assignmentSync = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(4), event -> syncAssignments(classroom, rows)));
        // assignmentSync.setCycleCount(javafx.animation.Animation.INDEFINITE);
        // assignmentSync.play();
    }

    private void renderAssignmentSnapshot(StudentApiClient.Classroom classroom, VBox rows,
                                          List<ClassChatSocket.AssignmentInfo> items) {
        List<Long> oldIds = visibleAssignments == null ? List.of()
                : visibleAssignments.stream().map(StudentApiClient.Assignment::id).toList();
        List<Long> newIds = items.stream().map(ClassChatSocket.AssignmentInfo::id).toList();
        if (visibleAssignments != null && !oldIds.equals(newIds)) {
            if (items.stream().anyMatch(item -> !oldIds.contains(item.id()))) showLiveNotice("Giáo viên vừa đăng bài tập mới trong lớp " + classroom.name());
            else showLiveNotice("Danh sách bài tập lớp " + classroom.name() + " vừa được cập nhật");
        }
        visibleAssignments = items.stream().map(item -> new StudentApiClient.Assignment(item.id(), item.title(),
                item.description(), item.fileName(), item.fileSize(), null, item.createdAt())).toList();
        rows.getChildren().clear();
        if (items.isEmpty()) { rows.getChildren().add(styled("Giáo viên chưa đăng bài tập cho lớp.", "student-muted")); return; }
        for (ClassChatSocket.AssignmentInfo item : items) {
            VBox fileInfo = new VBox(4, styled(item.title(), "student-card-title"),
                    styled(item.fileName() + "  ·  " + humanSize(item.fileSize()), "student-muted"));
            HBox row = new HBox(10, fileInfo, spacer(), link("Tải xuống", () -> download(classroom, item.id(), item.fileName())));
            row.setAlignment(Pos.CENTER_LEFT); rows.getChildren().add(row);
        }
    }

    private void updateAssignmentRows(StudentApiClient.Classroom classroom, VBox rows,
                                      List<StudentApiClient.Assignment> items, boolean notify) {
        List<StudentApiClient.Assignment> previous = visibleAssignments;
        List<Long> oldIds = previous == null ? List.of() : previous.stream().map(StudentApiClient.Assignment::id).toList();
        List<Long> newIds = items.stream().map(StudentApiClient.Assignment::id).toList();
        if (previous != null && oldIds.equals(newIds)) return;
        if (notify && previous != null) {
            boolean added = items.stream().anyMatch(item -> !oldIds.contains(item.id()));
            boolean removed = oldIds.stream().anyMatch(id -> !newIds.contains(id));
            if (added) showLiveNotice("Giáo viên vừa đăng tài liệu mới trong lớp " + classroom.name());
            else if (removed) showLiveNotice("Tài liệu trong lớp " + classroom.name() + " vừa được cập nhật");
        }
        visibleAssignments = List.copyOf(items);
        rows.getChildren().clear();
        if (items.isEmpty()) { rows.getChildren().add(styled("Giáo viên chưa đăng bài tập cho lớp.", "student-muted")); return; }
        for (StudentApiClient.Assignment item : items) {
            VBox fileInfo = new VBox(4, styled(item.title(), "student-card-title"),
                    styled(item.fileName() + "  ·  " + humanSize(item.fileSize()), "student-muted"));
            HBox row = new HBox(10, fileInfo, spacer(), link("Tải xuống", () -> download(classroom, item)));
            row.setAlignment(Pos.CENTER_LEFT); rows.getChildren().add(row);
        }
    }
    private void loadMessages(StudentApiClient.Classroom classroom, VBox target) {
        api.messages(classroom.id()).whenComplete((messages, error) -> Platform.runLater(() -> {
            if (target != chatMessages) return;
            target.getChildren().clear();
            if (error != null) { target.getChildren().add(styled(errorText(error), "student-muted")); return; }
            if (messages.isEmpty()) target.getChildren().add(styled("Chưa có tin nhắn. Hãy gửi lời chào đến lớp!", "student-muted"));
            for (StudentApiClient.ChatMessage message : messages) {
                VBox bubble = new VBox(4, styled(message.senderName(), "student-chat-sender"), styled(message.content(), "student-chat-message"));
                bubble.getStyleClass().add("student-chat-bubble"); target.getChildren().add(bubble);
            }
        }));
    }

    private void sendMessage(StudentApiClient.Classroom classroom, TextField input) {
        String text = input.getText().trim(); if (text.isEmpty()) return;
        if (chatSocket == null) { showError(new IllegalStateException("Kết nối chat chưa sẵn sàng.")); return; }
        chatSocket.send(text);
        input.clear();
    }

    private void appendChatMessage(String sender, String text) {
        if (chatMessages.getChildren().size() == 1 && chatMessages.getChildren().getFirst() instanceof Label label
                && label.getStyleClass().contains("student-muted")) chatMessages.getChildren().clear();
        VBox bubble = new VBox(4, styled(sender, "student-chat-sender"), styled(text, "student-chat-message"));
        bubble.getStyleClass().add("student-chat-bubble");
        chatMessages.getChildren().add(bubble);
    }

    private void download(StudentApiClient.Classroom classroom, StudentApiClient.Assignment assignment) {
        download(classroom, assignment.id(), assignment.fileName());
    }

    private void download(StudentApiClient.Classroom classroom, long assignmentId, String fileName) {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Lưu tài liệu"); chooser.setInitialFileName(fileName);
        File target = chooser.showSaveDialog(getScene().getWindow()); if (target == null) return;
        api.downloadAssignment(classroom.id(), assignmentId).whenComplete((bytes, error) -> Platform.runLater(() -> {
            if (error != null) { showError(error); return; }
            try { Files.write(target.toPath(), bytes); }
            catch (Exception exception) { showError(exception); }
        }));
    }

    private int syncTick;

    private void startLiveSync() {
        liveSync = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), event -> {
            // Tạm tắt polling nền 5 giây để thông báo tài liệu chỉ đến từ TCP socket.
            // Bật lại dòng dưới sau khi kiểm tra xong socket.
            // pollLiveData();
            if (++syncTick % 3 == 0) refreshSummaryMetrics();
        }));
        liveSync.setCycleCount(javafx.animation.Animation.INDEFINITE);
        liveSync.play();
        pollLiveData();
    }

    private void stopLiveSync() {
        if (liveSync != null) liveSync.stop();
        if (noticeTimer != null) noticeTimer.stop();
        stopChatRefresh();
    }

    private void pollLiveData() {
        api.classes().whenComplete((classes, error) -> Platform.runLater(() -> {
            if (error != null) {
                if (currentPage.equals("classes")) {
                    String message = errorText(error);
                    if (!message.equals(lastSyncError)) showLiveNotice("Khong dong bo duoc danh sach lop: " + message);
                    lastSyncError = message;
                }
                return;
            }
            lastSyncError = null;
            boolean changed = false;
            java.util.ArrayList<String> addedNames = new java.util.ArrayList<>();
            java.util.ArrayList<String> activityNames = new java.util.ArrayList<>();
            java.util.ArrayList<String> newResourceNames = new java.util.ArrayList<>();
            java.util.ArrayList<String> removedResourceNames = new java.util.ArrayList<>();
            Map<Long, StudentApiClient.Classroom> latest = new HashMap<>();
            for (StudentApiClient.Classroom classroom : classes) {
                latest.put(classroom.id(), classroom);
                StudentApiClient.Classroom previous = knownClasses.get(classroom.id());
                if (liveSyncPrimed && previous == null) {
                    addedNames.add(classroom.name()); changed = true;
                } else if (liveSyncPrimed && previous != null) {
                    if (classroom.messageCount() > previous.messageCount()) activityNames.add(classroom.name());
                    if (classroom.assignmentCount() > previous.assignmentCount()) newResourceNames.add(classroom.name());
                    else if (classroom.assignmentCount() < previous.assignmentCount()) removedResourceNames.add(classroom.name());
                    if (classroom.messageCount() != previous.messageCount()
                            || classroom.assignmentCount() != previous.assignmentCount()) changed = true;
                }
            }
            if (liveSyncPrimed && latest.size() != knownClasses.size()) changed = true;
            knownClasses.clear(); knownClasses.putAll(latest);
            liveSyncPrimed = true;
            classesNav.setText("▦   Lớp học  ·  " + classes.size());
            if (changed && currentPage.equals("classes")) renderClasses(classes);
            if (changed && currentPage.equals("overview")) refreshSummaryMetrics();
            if (!addedNames.isEmpty()) showLiveNotice("Giáo viên đã thêm bạn vào lớp: " + String.join(", ", addedNames));
            else if (!newResourceNames.isEmpty()) showLiveNotice("Giáo viên vừa đăng bài tập trong lớp: " + String.join(", ", newResourceNames));
            else if (!removedResourceNames.isEmpty()) showLiveNotice("Tài liệu trong lớp đã được cập nhật: " + String.join(", ", removedResourceNames));
            else if (!activityNames.isEmpty()) showLiveNotice("Có tin nhắn mới trong lớp: " + String.join(", ", activityNames));
        }));
    }

    private void refreshSummaryMetrics() {
        if (!currentPage.equals("overview")) return;
        api.summary().whenComplete((summary, error) -> Platform.runLater(() -> {
            if (error != null || !currentPage.equals("overview")) return;
            if (classMetricValue != null) classMetricValue.setText(String.valueOf(summary.classes()));
            if (examMetricValue != null) examMetricValue.setText(String.valueOf(summary.availableExams()));
            if (resultMetricValue != null) resultMetricValue.setText(String.valueOf(summary.completed()));
        }));
    }

    private void showLiveNotice(String message) {
        liveNotice.setText(message + "   ·   Nhấn để xem lớp học");
        liveNotice.setManaged(true);
        liveNotice.setVisible(true);
        liveNotice.setOnMouseClicked(event -> showClasses());
        if (noticeTimer != null) noticeTimer.stop();
        noticeTimer = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(9), event -> {
            liveNotice.setVisible(false); liveNotice.setManaged(false);
        }));
        noticeTimer.play();
    }

    private VBox scoreCard(String title, String classroom, String score, String date) {
        VBox card = new VBox(8, styled(title, "student-card-title"), styled(classroom + "  ·  " + date, "student-muted"),
                styled(score, "student-score-value"));
        card.getStyleClass().add("student-panel"); return card;
    }

    private VBox infoCard(String title, String description) {
        VBox card = new VBox(7, styled(title, "student-card-title"), styled(description, "student-muted"));
        card.getStyleClass().add("student-panel"); return card;
    }

    private Label chip(String text) { return styled(text, "student-chip"); }
    private void stopChatRefresh() {
        if (chatRefresh != null) { chatRefresh.stop(); chatRefresh = null; }
        if (assignmentSync != null) { assignmentSync.stop(); assignmentSync = null; }
        if (chatSocket != null) { chatSocket.close(); chatSocket = null; }
        chatMessages = null;
    }
    private void showError(Throwable error) { info("Không thể hoàn thành", errorText(error)); }
    private static String errorText(Throwable error) {
        Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof StudentApiClient.ApiException apiError && apiError.status() == 404)
            return "Không tìm thấy dữ liệu hoặc bạn không thuộc lớp này.";
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
    private static String studentStatus(String status) {
        return switch (status) {
            case "IN_PROGRESS" -> "Đang làm bài";
            case "SUBMITTED" -> "Đã nộp";
            case "GRADED" -> "Đã chấm";
            case "LOCKED" -> "Đã khóa";
            default -> status;
        };
    }
    private static String displayDate(String value) { return value == null ? "—" : value.replace('T', ' ').replaceAll("\\.\\d+Z$", " UTC"); }
    private static String humanSize(long bytes) { return bytes < 1024 ? bytes + " B" : String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0); }
    private static String firstName(String name) { if (name == null || name.isBlank()) return "bạn"; String[] p = name.trim().split("\\s+"); return p[p.length - 1]; }
    private static String initials(String name) {
        if (name == null || name.isBlank()) return "HS";
        String[] p = name.trim().split("\\s+");
        return p.length == 1 ? p[0].substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                : (p[0].substring(0, 1) + p[p.length - 1].substring(0, 1)).toUpperCase(java.util.Locale.ROOT);
    }
    private static Region spacer() { Region region = new Region(); HBox.setHgrow(region, Priority.ALWAYS); return region; }
    private static Label styled(String text, String style) { Label label = new Label(text); label.getStyleClass().add(style); label.setWrapText(true); return label; }
    private static void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION); alert.setTitle(title); alert.setHeaderText(title); alert.setContentText(message); alert.showAndWait();
    }
}
