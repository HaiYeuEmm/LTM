package vn.onlineexam.client;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Spinner;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TeacherDashboard extends BorderPane {
    private final String email;
    private final String fullName;
    private final TeacherApiClient api;
    private final Runnable onLogout;
    private final VBox content = new VBox(16);
    private javafx.animation.Timeline chatRefresh;
    private ClassChatSocket chatSocket;
    private VBox chatMessages;
    private Button selectedNav;

    public TeacherDashboard(String email, String fullName, TeacherApiClient api, Runnable onLogout) {
        this.email = email;
        this.fullName = fullName;
        this.api = api;
        this.onLogout = onLogout;
        getStyleClass().add("teacher-root");
        setLeft(buildSidebar());
        content.getStyleClass().add("teacher-page-content");
        content.setPadding(new Insets(28));
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("teacher-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(scroll);
        showOverview();
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(12);
        sidebar.getStyleClass().add("teacher-sidebar");
        sidebar.setPadding(new Insets(24, 18, 20, 18));
        sidebar.setPrefWidth(245);
        HBox brand = new HBox(10, styled("E", "teacher-brand-mark"), styled("examly", "teacher-brand-name"));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("teacher-sidebar-brand");
        HBox profile = new HBox(10, styled(initials(fullName), "teacher-avatar"),
                new VBox(4, styled(fullName, "teacher-profile-name"), styled(email, "teacher-profile-email"),
                        styled("GIÁO VIÊN", "teacher-profile-role")));
        profile.setAlignment(Pos.CENTER_LEFT);
        profile.getStyleClass().add("teacher-user-card");
        Button overview = nav("◈   Tổng quan", this::showOverview);
        Button classes = nav("▦   Lớp học", this::showClasses);
        Button exams = nav("✦   Đề thi", this::showExams);
        Button questions = nav("☷   Ngân hàng câu hỏi", () -> info("Ngân hàng câu hỏi", "Các bảng đã có trong database; chức năng quản lý câu hỏi sẽ được nối tiếp."));
        Button results = nav("◷   Kết quả", () -> info("Kết quả", "Chưa có bài thi được triển khai nên chưa có dữ liệu kết quả."));
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        Button logout = nav("↪   Đăng xuất", onLogout);
        sidebar.getChildren().addAll(brand, styled("KHÔNG GIAN LÀM VIỆC", "teacher-side-caption"),
                overview, classes, exams, questions, results, spacer, profile, logout);
        setActiveNav(overview);
        return sidebar;
    }

    private Button nav(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("teacher-nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(event -> { setActiveNav(button); action.run(); });
        return button;
    }

    private void setActiveNav(Button button) {
        if (selectedNav != null) selectedNav.getStyleClass().remove("teacher-nav-active");
        selectedNav = button;
        if (!button.getStyleClass().contains("teacher-nav-active")) button.getStyleClass().add("teacher-nav-active");
    }

    private Button actionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("teacher-action-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private Button linkAction(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("teacher-link-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private void showOverview() {
        stopChatRefresh();
        content.getChildren().setAll(styled("Đang tải không gian làm việc…", "teacher-row-subtitle"));
        api.summary().whenComplete((summary, error) -> Platform.runLater(() -> {
            if (error != null) { content.getChildren().setAll(styled(errorText(error), "teacher-row-subtitle")); return; }
            HBox metrics = new HBox(14,
                    metricCard("01", "LỚP ĐANG DẠY", summary.classes(), "Không gian học tập", "metric-blue"),
                    metricCard("02", "HỌC SINH", summary.students(), "Đang tham gia các lớp", "metric-teal"),
                    metricCard("03", "ĐỀ THI", summary.exams(), "Bài đánh giá đã tạo", "metric-violet"));
            for (Node node : metrics.getChildren()) HBox.setHgrow(node, Priority.ALWAYS);
            HBox quickActions = new HBox(12, actionButton("＋  Tạo lớp học", this::promptCreateClass),
                    actionButton("＋  Soạn đề thi", this::promptCreateQuiz));
            content.getChildren().setAll(heroBanner(), metrics,
                    sectionHeader("Bắt đầu nhanh", "Các thao tác thường dùng"), quickActions,
                    card("Mẹo sử dụng", "Mở một lớp để quản lý học sinh, trò chuyện trong nhóm và đăng tài liệu bài tập."));
        }));
    }

    private HBox heroBanner() {
        VBox copy = new VBox(8, styled("KHÔNG GIAN GIÁO VIÊN  ·  EXAMLY", "teacher-hero-eyebrow"),
                styled("Xin chào, " + firstName(fullName) + "!", "teacher-hero-title"),
                styled("Quản lý lớp học và tổ chức hoạt động học tập của bạn tại đây.", "teacher-hero-copy"));
        HBox hero = new HBox(copy, spacer(), styled("✦", "teacher-hero-emblem"));
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("teacher-hero");
        return hero;
    }

    private VBox metricCard(String number, String title, int value, String note, String accent) {
        HBox index = new HBox(styled(number, "teacher-metric-index"), spacer(), styled("●", "teacher-metric-dot"));
        VBox metric = new VBox(9, index, styled(title, "teacher-metric-caption"),
                styled(String.valueOf(value), "teacher-metric-value"), styled(note, "teacher-row-subtitle"));
        metric.getStyleClass().addAll("teacher-metric-card", accent);
        return metric;
    }

    private HBox sectionHeader(String title, String subtitle) {
        return new HBox(new VBox(4, styled(title, "teacher-section-title"), styled(subtitle, "teacher-row-subtitle")));
    }

    private void showClasses() {
        stopChatRefresh();
        content.getChildren().setAll(styled("Lớp học", "teacher-page-title"),
                styled("Tạo lớp, xem mã lớp và thêm học sinh theo email. Thành viên mới cũng được thêm vào chat lớp.", "teacher-row-subtitle"),
                actionButton("＋  Tạo lớp mới", this::promptCreateClass), styled("Đang tải lớp…", "teacher-row-subtitle"));
        Node loading = content.getChildren().get(3);
        api.classes().whenComplete((classes, error) -> Platform.runLater(() -> {
            content.getChildren().remove(loading);
            if (error != null) { content.getChildren().add(styled(errorText(error), "teacher-row-subtitle")); return; }
            if (classes.isEmpty()) content.getChildren().add(styled("Bạn chưa tạo lớp nào.", "teacher-row-subtitle"));
            for (TeacherApiClient.Classroom classroom : classes) {
                VBox item = new VBox(8);
                item.getStyleClass().add("teacher-list-card");
                item.getChildren().addAll(styled(classroom.name(), "teacher-card-title"),
                        styled("Mã lớp: " + classroom.classCode() + " · " + classroom.studentCount() + " học sinh", "teacher-row-subtitle"));
                HBox actions = new HBox(10, linkAction("Thêm danh sách", () -> promptAddStudents(classroom)),
                        linkAction("Thành viên", () -> showMembers(classroom)), linkAction("Mở nhóm lớp", () -> showClassDetails(classroom)));
                item.getChildren().add(actions);
                content.getChildren().add(item);
            }
        }));
    }

    private void showExams() {
        stopChatRefresh();
        content.getChildren().setAll(styled("Đề thi", "teacher-page-title"),
                styled("Tạo đề nháp và xem dữ liệu đã lưu.", "teacher-row-subtitle"),
                actionButton("＋  Tạo đề thi", this::promptCreateQuiz), styled("Đang tải đề thi…", "teacher-row-subtitle"));
        Node loading = content.getChildren().get(3);
        api.exams().whenComplete((exams, error) -> Platform.runLater(() -> {
            content.getChildren().remove(loading);
            if (error != null) { content.getChildren().add(styled(errorText(error), "teacher-row-subtitle")); return; }
            if (exams.isEmpty()) content.getChildren().add(styled("Bạn chưa tạo đề thi nào.", "teacher-row-subtitle"));
            for (TeacherApiClient.Exam exam : exams) {
                String classroom = exam.classroomName() == null ? "Chưa gắn lớp" : "Lớp: " + exam.classroomName();
                content.getChildren().add(card(exam.title(), classroom + " · " + exam.status()
                        + (exam.description() == null ? "" : " · " + exam.description())));
            }
        }));
    }

    private void promptCreateClass() {
        TextInputDialog name = new TextInputDialog(); name.setTitle("Tạo lớp"); name.setHeaderText("Tên lớp mới"); name.setContentText("Tên:");
        name.showAndWait().ifPresent(value -> {
            if (value.isBlank()) return;
            TextInputDialog desc = new TextInputDialog(); desc.setTitle("Mô tả"); desc.setHeaderText("Mô tả lớp (có thể để trống)");
            desc.showAndWait().ifPresentOrElse(description -> createClass(value, description), () -> createClass(value, ""));
        });
    }
    private void createClass(String name, String description) {
        api.createClass(name, description).whenComplete((created, error) -> Platform.runLater(() -> {
            if (error != null) showError(error); else showClasses();
        }));
    }
    private void promptAddStudents(TeacherApiClient.Classroom classroom) {
        TextArea emailInput = new TextArea();
        emailInput.setPromptText("Mỗi dòng một email học sinh\nstudent1@example.com\nstudent2@example.com");
        emailInput.setPrefRowCount(8);
        javafx.scene.control.Dialog<List<String>> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Thêm danh sách học sinh");
        dialog.setHeaderText("Nhập tối đa 100 email, mỗi dòng một học sinh");
        dialog.getDialogPane().setContent(emailInput);
        ButtonType addButton = new ButtonType(
        "Thêm vào lớp",
        ButtonType.APPLY.getButtonData()
                                    );
        dialog.getDialogPane().getButtonTypes().addAll(addButton, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == addButton
                ? emailInput.getText().lines().flatMap(line -> java.util.Arrays.stream(line.split("[,;\\s]+")))
                    .map(String::trim).filter(email -> !email.isBlank()).distinct().toList()
                : null);
        dialog.showAndWait().ifPresent(emails -> {
            if (emails.isEmpty()) { showError(new IllegalArgumentException("Bạn chưa nhập email học sinh.")); return; }
            api.addStudents(classroom.id(), emails).whenComplete((added, error) -> Platform.runLater(() -> {
                if (error != null) showError(error);
                else { info("Đã thêm học sinh", "Đã thêm " + added.size() + " học sinh và đồng bộ vào nhóm chat."); showClassDetails(classroom); }
            }));
        });
    }
    private void showMembers(TeacherApiClient.Classroom classroom) {
        api.members(classroom.id()).whenComplete((members, error) -> Platform.runLater(() -> {
            if (error != null) { showError(error); return; }
            String text = members.isEmpty() ? "Lớp chưa có học sinh." : members.stream()
                    .map(member -> member.fullName() + " — " + member.email() + " (" + member.status() + ")")
                    .collect(java.util.stream.Collectors.joining("\n"));
            info("Thành viên · " + classroom.name(), text);
        }));
    }

    private void showClassDetails(TeacherApiClient.Classroom classroom) {
        stopChatRefresh();
        content.getChildren().clear();
        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        VBox heading = new VBox(5, styled(classroom.name(), "teacher-page-title"),
                styled("Mã lớp: " + classroom.classCode() + " · " + classroom.studentCount() + " học sinh", "teacher-row-subtitle"));
        Region gap = spacer();
        titleRow.getChildren().addAll(heading, gap, linkAction("← Danh sách lớp", this::showClasses));
        content.getChildren().add(titleRow);

        VBox membersCard = new VBox(10);
        membersCard.getStyleClass().add("teacher-list-card");
        HBox membersHeader = new HBox(10, styled("Thành viên lớp", "teacher-card-title"), spacer(),
                linkAction("＋ Thêm danh sách", () -> promptAddStudents(classroom)));
        membersCard.getChildren().add(membersHeader);
        Label membersLoading = styled("Đang tải thành viên…", "teacher-row-subtitle");
        membersCard.getChildren().add(membersLoading);
        api.members(classroom.id()).whenComplete((members, error) -> Platform.runLater(() -> {
            membersCard.getChildren().remove(membersLoading);
            if (error != null) { membersCard.getChildren().add(styled(errorText(error), "teacher-row-subtitle")); return; }
            if (members.isEmpty()) membersCard.getChildren().add(styled("Chưa có học sinh trong lớp.", "teacher-row-subtitle"));
            for (TeacherApiClient.Member member : members) {
                membersCard.getChildren().add(styled(member.fullName() + " · " + member.email() + " · " + member.status(), "teacher-row-subtitle"));
            }
        }));
        content.getChildren().add(membersCard);

        VBox assignmentRows = new VBox(9);
        VBox chatCard = new VBox(10);
        chatCard.getStyleClass().add("teacher-list-card");
        chatMessages = new VBox(8);
        HBox chatHeader = new HBox(10, styled("Tin nhắn nhóm", "teacher-card-title"), spacer(),
                linkAction("Làm mới", () -> loadMessages(classroom, chatMessages)));
        ScrollPane messageScroll = new ScrollPane(chatMessages);
        messageScroll.setFitToWidth(true);
        messageScroll.setPrefHeight(240);
        messageScroll.getStyleClass().add("teacher-chat-scroll");
        TextField input = new TextField();
        input.setPromptText("Viết tin nhắn cho nhóm lớp…");
        Button send = actionButton("Gửi", () -> sendMessage(classroom, input));
        input.setOnAction(event -> send.fire());
        HBox composer = new HBox(9, input, send);
        HBox.setHgrow(input, Priority.ALWAYS);
        chatCard.getChildren().addAll(chatHeader, messageScroll, composer);
        content.getChildren().add(chatCard);
        loadMessages(classroom, chatMessages);
        chatSocket = api.openChat(classroom.id(), message -> Platform.runLater(() -> {
            if (message.assignments() != null) renderAssignmentSnapshot(classroom, assignmentRows, message.assignments());
            else if (chatMessages != null) appendChatMessage(message.senderName(), message.content());
        }), error -> Platform.runLater(() -> {
            if (chatMessages != null) chatMessages.getChildren().setAll(styled("Mất kết nối chat Socket: " + errorText(error), "teacher-row-subtitle"));
        }));

        VBox assignmentsCard = new VBox(10);
        assignmentsCard.getStyleClass().add("teacher-list-card");
        HBox assignmentHeader = new HBox(10, styled("Bài tập trong nhóm", "teacher-card-title"), spacer(),
                actionButton("＋ Đăng bài tập", () -> chooseAssignmentFile(classroom, assignmentRows)));
        assignmentsCard.getChildren().addAll(assignmentHeader, assignmentRows);
        content.getChildren().add(assignmentsCard);
        assignmentRows.getChildren().setAll(styled("Đang kết nối socket để tải bài tập…", "teacher-row-subtitle"));
    }

    private void loadMessages(TeacherApiClient.Classroom classroom, VBox target) {
        api.messages(classroom.id()).whenComplete((messages, error) -> Platform.runLater(() -> {
            if (target != chatMessages) return;
            target.getChildren().clear();
            if (error != null) { target.getChildren().add(styled(errorText(error), "teacher-row-subtitle")); return; }
            if (messages.isEmpty()) target.getChildren().add(styled("Chưa có tin nhắn. Hãy bắt đầu trao đổi với lớp.", "teacher-row-subtitle"));
            for (TeacherApiClient.ChatMessage message : messages) {
                VBox bubble = new VBox(4, styled(message.senderName(), "teacher-chat-sender"),
                        styled(message.content(), "teacher-chat-message"));
                bubble.getStyleClass().add("teacher-chat-bubble");
                target.getChildren().add(bubble);
            }
        }));
    }

    private void sendMessage(TeacherApiClient.Classroom classroom, TextField input) {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        input.setDisable(true);
        input.setDisable(false);
        if (chatSocket == null) { showError(new IllegalStateException("Kết nối chat chưa sẵn sàng.")); return; }
        chatSocket.send(text);
        input.clear();
    }

    private void appendChatMessage(String sender, String text) {
        if (chatMessages.getChildren().size() == 1 && chatMessages.getChildren().getFirst() instanceof Label label
                && label.getStyleClass().contains("teacher-row-subtitle")) chatMessages.getChildren().clear();
        VBox bubble = new VBox(4, styled(sender, "teacher-chat-sender"), styled(text, "teacher-chat-message"));
        bubble.getStyleClass().add("teacher-chat-bubble");
        chatMessages.getChildren().add(bubble);
    }

    private void renderAssignmentSnapshot(TeacherApiClient.Classroom classroom, VBox rows,
                                          List<ClassChatSocket.AssignmentInfo> assignments) {
        rows.getChildren().clear();
        if (assignments.isEmpty()) { rows.getChildren().add(styled("Chưa có bài tập được đăng.", "teacher-row-subtitle")); return; }
        for (ClassChatSocket.AssignmentInfo assignment : assignments) {
            VBox details = new VBox(4, styled(assignment.title(), "teacher-card-title"),
                    styled(assignment.fileName() + " · " + humanSize(assignment.fileSize()), "teacher-row-subtitle"));
            HBox row = new HBox(10, details, spacer(),
                    linkAction("Tải xuống", () -> saveAssignment(classroom, assignment.id(), assignment.fileName())),
                    linkAction("Xóa", () -> confirmDeleteAssignment(classroom, assignment.id(), assignment.title())));
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        }
    }

    private void chooseAssignmentFile(TeacherApiClient.Classroom classroom, VBox rows) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn tệp bài tập");
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) return;
        TextInputDialog title = new TextInputDialog(file.getName());
        title.setTitle("Đăng bài tập");
        title.setHeaderText("Đặt tên bài tập cho nhóm lớp");
        title.setContentText("Tiêu đề:");
        styleTeacherDialog(title);
        title.showAndWait().ifPresent(value -> {
            if (value.isBlank()) return;
            api.uploadAssignment(classroom.id(), value, "", file.toPath()).whenComplete((assignment, error) -> Platform.runLater(() -> {
                if (error != null) showError(error);
                else info("Đã đăng bài tập", "Tệp đã được lưu; danh sách sẽ được cập nhật qua socket.");
            }));
        });
    }

    private void confirmDeleteAssignment(TeacherApiClient.Classroom classroom,
                                         TeacherApiClient.Assignment assignment, VBox rows) {
        confirmDeleteAssignment(classroom, assignment.id(), assignment.title());
    }

    private void confirmDeleteAssignment(TeacherApiClient.Classroom classroom, long assignmentId, String title) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xóa bài tập");
        confirm.setHeaderText("Xóa “" + title + "” khỏi lớp?");
        confirm.setContentText("Học sinh sẽ không thể tải tệp này nữa.");
        confirm.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button ->
                api.deleteAssignment(classroom.id(), assignmentId).whenComplete((ignored, error) -> Platform.runLater(() -> {
                    if (error != null) showError(error);
                    else info("Đã xóa", "Bài tập đã được xóa; danh sách sẽ được cập nhật qua socket.");
                })));
    }

    private void saveAssignment(TeacherApiClient.Classroom classroom, TeacherApiClient.Assignment assignment) {
        saveAssignment(classroom, assignment.id(), assignment.fileName());
    }

    private void saveAssignment(TeacherApiClient.Classroom classroom, long assignmentId, String fileName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu tệp bài tập");
        chooser.setInitialFileName(fileName);
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        api.downloadAssignment(classroom.id(), assignmentId).whenComplete((bytes, error) -> Platform.runLater(() -> {
            if (error != null) { showError(error); return; }
            try { Files.write(file.toPath(), bytes); }
            catch (Exception exception) { showError(exception); }
        }));
    }

    private void stopChatRefresh() {
        if (chatRefresh != null) { chatRefresh.stop(); chatRefresh = null; }
        if (chatSocket != null) { chatSocket.close(); chatSocket = null; }
        chatMessages = null;
    }

    private static String humanSize(long size) {
        return size < 1024 ? size + " B" : String.format(java.util.Locale.ROOT, "%.1f KB", size / 1024.0);
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
    private void promptCreateQuiz() {
        api.classes().whenComplete((classes, loadError) -> Platform.runLater(() -> {
            if (loadError != null) { showError(loadError); return; }
            if (classes.isEmpty()) { showError(new IllegalStateException("Bạn cần tạo lớp học trước khi soạn quiz.")); return; }

            ComboBox<TeacherApiClient.Classroom> classroom = new ComboBox<>();
            classroom.getItems().setAll(classes);
            classroom.setValue(classes.getFirst());
            classroom.setMaxWidth(Double.MAX_VALUE);
            classroom.setButtonCell(new javafx.scene.control.ListCell<>() {
                @Override protected void updateItem(TeacherApiClient.Classroom item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "Chọn lớp" : item.name() + " · " + item.classCode());
                }
            });
            classroom.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
                @Override protected void updateItem(TeacherApiClient.Classroom item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.name() + " · " + item.classCode());
                }
            });
            TextField title = new TextField(); title.setPromptText("Ví dụ: Kiểm tra Java cơ bản");
            TextArea description = new TextArea(); description.setPromptText("Mô tả quiz (không bắt buộc)"); description.setPrefRowCount(2);
            Spinner<Integer> duration = new Spinner<>(1, 600, 30); duration.setEditable(true);
            VBox questionCards = new VBox(12);
            List<QuizQuestionEditor> editors = new java.util.ArrayList<>();
            Runnable addQuestion = () -> {
                QuizQuestionEditor editor = new QuizQuestionEditor(editors.size() + 1);
                editors.add(editor); questionCards.getChildren().add(editor.node());
            };
            addQuestion.run();
            Button addQuestionButton = new Button("+ Thêm câu hỏi");
            addQuestionButton.setOnAction(event -> addQuestion.run());
            VBox form = new VBox(10, styled("Lớp học", "teacher-row-subtitle"), classroom,
                    styled("Tên quiz", "teacher-row-subtitle"), title,
                    styled("Mô tả", "teacher-row-subtitle"), description,
                    styled("Thời lượng (phút)", "teacher-row-subtitle"), duration,
                    styled("Câu hỏi và đáp án", "teacher-card-title"), questionCards, addQuestionButton);
            form.getStyleClass().add("teacher-dialog-form");
            ScrollPane scroll = new ScrollPane(form); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(540);
            scroll.getStyleClass().add("teacher-dialog-scroll");

            javafx.scene.control.Dialog<ButtonType> dialog = new javafx.scene.control.Dialog<>();
            dialog.setTitle("Soạn quiz cho lớp"); dialog.setHeaderText("Tạo quiz trắc nghiệm và mở cho học sinh làm");
            styleTeacherDialog(dialog);
            dialog.getDialogPane().setContent(scroll);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.getDialogPane().setPrefWidth(760);
            dialog.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> {
                try {
                    List<TeacherApiClient.QuizQuestionInput> questions = editors.stream().map(QuizQuestionEditor::value).toList();
                    if (title.getText().isBlank()) throw new IllegalArgumentException("Hãy nhập tên quiz.");
                    api.createQuiz(classroom.getValue().id(), title.getText().trim(), description.getText().trim(),
                            duration.getValue(), questions).whenComplete((created, error) -> Platform.runLater(() -> {
                        if (error != null) showError(error);
                        else { info("Đã tạo quiz", "Quiz đã được gắn với lớp " + classroom.getValue().name() + " và học sinh có thể bắt đầu làm."); showExams(); }
                    }));
                } catch (RuntimeException error) { showError(error); }
            });
        }));
    }

    private final class QuizQuestionEditor {
        private final TextArea question = new TextArea();
        private final List<TextField> choices = new java.util.ArrayList<>();
        private final List<RadioButton> correctOptions = new java.util.ArrayList<>();
        private final VBox card = new VBox(8);
        private QuizQuestionEditor(int number) {
            card.getStyleClass().addAll("teacher-list-card", "teacher-quiz-question");
            question.setPromptText("Nhập nội dung câu hỏi " + number);
            question.setPrefRowCount(2);
            ToggleGroup correct = new ToggleGroup();
            card.getChildren().addAll(styled("Câu hỏi " + number, "teacher-card-title"), question);
            for (int index = 0; index < 4; index++) {
                TextField choice = new TextField(); choice.setPromptText("Đáp án " + (index + 1));
                RadioButton radio = new RadioButton("Đúng"); radio.setToggleGroup(correct);
                if (index == 0) radio.setSelected(true);
                choices.add(choice); correctOptions.add(radio);
                HBox row = new HBox(9, radio, choice); HBox.setHgrow(choice, Priority.ALWAYS);
                card.getChildren().add(row);
            }
        }
        Node node() { return card; }
        TeacherApiClient.QuizQuestionInput value() {
            if (question.getText().isBlank() || choices.stream().anyMatch(choice -> choice.getText().isBlank()))
                throw new IllegalArgumentException("Điền nội dung câu hỏi và đủ 4 đáp án.");
            List<TeacherApiClient.QuizChoiceInput> answers = new java.util.ArrayList<>();
            for (int index = 0; index < choices.size(); index++)
                answers.add(new TeacherApiClient.QuizChoiceInput(choices.get(index).getText().trim(), correctOptions.get(index).isSelected()));
            return new TeacherApiClient.QuizQuestionInput(question.getText().trim(), answers);
        }
    }

    private void styleTeacherDialog(javafx.scene.control.Dialog<?> dialog) {
        dialog.getDialogPane().getStyleClass().add("teacher-form-dialog");
        var stylesheet = getClass().getResource("/styles/teacher.css");
        if (stylesheet != null) dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
    }

    private void promptCreateExam() {
        TextInputDialog title = new TextInputDialog(); title.setTitle("Tạo đề thi"); title.setHeaderText("Đề thi được lưu ở trạng thái nháp"); title.setContentText("Tiêu đề:");
        title.showAndWait().ifPresent(value -> {
            if (value.isBlank()) return;
            TextInputDialog desc = new TextInputDialog(); desc.setTitle("Mô tả"); desc.setHeaderText("Mô tả đề thi (có thể để trống)");
            desc.showAndWait().ifPresentOrElse(description -> createExam(value, description), () -> createExam(value, ""));
        });
    }
    private void createExam(String title, String description) {
        api.createExam(title, description).whenComplete((created, error) -> Platform.runLater(() -> {
            if (error != null) showError(error); else showExams();
        }));
    }
    private VBox card(String title, String detail) {
        VBox card = new VBox(8, styled(title, "teacher-card-title"), styled(detail, "teacher-row-subtitle"));
        card.getStyleClass().add("teacher-list-card"); return card;
    }
    private void showError(Throwable error) { info("Lỗi thao tác", errorText(error)); }
    private static String errorText(Throwable error) {
        Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof TeacherApiClient.ApiException apiError) {
            try {
                com.google.gson.JsonObject body = com.google.gson.JsonParser.parseString(apiError.getMessage()).getAsJsonObject();
                if (body.has("detail")) return body.get("detail").getAsString();
                if (body.has("message")) return body.get("message").getAsString();
            } catch (RuntimeException ignored) {
                // Non-JSON responses are shown as-is below.
            }
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
    private static String firstName(String name) {
        if (name == null || name.isBlank()) return "giáo viên";
        String[] words = name.trim().split("\\s+");
        return words[words.length - 1];
    }
    private static String initials(String name) {
        if (name == null || name.isBlank()) return "GV";
        String[] words = name.trim().split("\\s+");
        return words.length == 1 ? words[0].substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                : (words[0].substring(0, 1) + words[words.length - 1].substring(0, 1)).toUpperCase(java.util.Locale.ROOT);
    }
    private static Label styled(String text, String style) { Label label = new Label(text); label.getStyleClass().add(style); label.setWrapText(true); return label; }
    private static void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION); alert.setTitle(title); alert.setHeaderText(title); alert.setContentText(message); alert.showAndWait();
    }
}
