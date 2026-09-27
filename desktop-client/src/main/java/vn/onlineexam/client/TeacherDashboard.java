package vn.onlineexam.client;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class TeacherDashboard extends BorderPane {
    private static final String[] NAV_ITEMS = {
            "Tổng quan", "Lớp học", "Đề thi", "Ngân hàng câu hỏi", "Kết quả"
    };

    private final String email;
    private final Runnable onLogout;
    private final Map<String, Button> navigationButtons = new LinkedHashMap<>();
    private final VBox pageContent = new VBox(22);
    private final Label pageTitle = new Label();
    private final Label pageSubtitle = new Label();

    public TeacherDashboard(String email, Runnable onLogout) {
        this.email = email;
        this.onLogout = onLogout;
        pageTitle.getStyleClass().add("teacher-page-title");
        pageSubtitle.getStyleClass().add("teacher-page-subtitle");
        getStyleClass().add("teacher-root");
        setLeft(buildSidebar());
        setCenter(buildMainArea());
        showPage("Tổng quan");
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(0);
        sidebar.getStyleClass().add("teacher-sidebar");
        sidebar.setPrefWidth(248);
        sidebar.setMinWidth(220);
        sidebar.setPadding(new Insets(28, 18, 20, 18));

        HBox brand = new HBox(11);
        brand.setAlignment(Pos.CENTER_LEFT);
        Label mark = new Label("E");
        mark.getStyleClass().add("teacher-brand-mark");
        Label name = new Label("examly");
        name.getStyleClass().add("teacher-brand-name");
        brand.getChildren().addAll(mark, name);

        Label workspaceLabel = new Label("KHÔNG GIAN LÀM VIỆC");
        workspaceLabel.getStyleClass().add("teacher-side-caption");
        VBox nav = new VBox(7);
        nav.getStyleClass().add("teacher-navigation");
        for (String item : NAV_ITEMS) {
            Button button = new Button(item);
            button.getStyleClass().add("teacher-nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setOnAction(event -> showPage(item));
            navigationButtons.put(item, button);
            nav.getChildren().add(button);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        HBox profile = new HBox(11);
        profile.getStyleClass().add("teacher-profile");
        profile.setAlignment(Pos.CENTER_LEFT);
        Label avatar = new Label(initials(email));
        avatar.getStyleClass().add("teacher-avatar");
        VBox identity = new VBox(3);
        Label teacherName = new Label(displayName(email));
        teacherName.getStyleClass().add("teacher-profile-name");
        Label teacherRole = new Label("Giáo viên");
        teacherRole.getStyleClass().add("teacher-profile-role");
        identity.getChildren().addAll(teacherName, teacherRole);
        profile.getChildren().addAll(avatar, identity);

        Button logout = new Button("Đăng xuất");
        logout.getStyleClass().add("teacher-logout-button");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(event -> onLogout.run());

        sidebar.getChildren().addAll(brand, workspaceLabel, nav, spacer, profile, logout);
        VBox.setMargin(workspaceLabel, new Insets(42, 0, 13, 10));
        VBox.setMargin(profile, new Insets(0, 0, 15, 0));
        return sidebar;
    }

    private VBox buildMainArea() {
        VBox main = new VBox(0);
        main.getStyleClass().add("teacher-main");

        HBox topBar = new HBox(16);
        topBar.getStyleClass().add("teacher-topbar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        VBox titleStack = new VBox(4, pageTitle, pageSubtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        TextField search = new TextField();
        search.setPromptText("Tìm kiếm...");
        search.getStyleClass().add("teacher-search");
        search.setMaxWidth(250);
        Label notification = new Label("2");
        notification.getStyleClass().add("teacher-notification");
        Button quickAction = new Button("＋  Tạo đề thi");
        quickAction.getStyleClass().add("teacher-primary-button");
        quickAction.setOnAction(event -> showPlaceholder("Tạo đề thi"));
        topBar.getChildren().addAll(titleStack, spacer, search, notification, quickAction);

        pageContent.getStyleClass().add("teacher-page-content");
        pageContent.setPadding(new Insets(25, 34, 34, 34));
        main.getChildren().addAll(topBar, pageContent);
        VBox.setVgrow(pageContent, Priority.ALWAYS);
        return main;
    }

    private void showPage(String page) {
        pageTitle.setText(page);
        pageSubtitle.setText("Xin chào, " + displayName(email) + " — chúc bạn một ngày làm việc hiệu quả.");
        navigationButtons.forEach((name, button) -> {
            button.getStyleClass().remove("teacher-nav-active");
            if (name.equals(page)) {
                button.getStyleClass().add("teacher-nav-active");
            }
        });

        pageContent.getChildren().clear();
        switch (page) {
            case "Tổng quan" -> showOverview();
            case "Lớp học" -> showClasses();
            case "Đề thi" -> showExams();
            case "Ngân hàng câu hỏi" -> showQuestions();
            case "Kết quả" -> showResults();
            default -> showOverview();
        }
    }

    private void showOverview() {
        HBox sampleNotice = new HBox(new Label("BẢN XEM TRƯỚC  ·  Số liệu minh họa, chưa lấy từ server"));
        sampleNotice.getStyleClass().add("teacher-sample-notice");

        GridPane stats = new GridPane();
        stats.setHgap(16);
        stats.setVgap(16);
        stats.add(statCard("Lớp đang dạy", "08", "＋ 2 lớp trong học kỳ này", "blue"), 0, 0);
        stats.add(statCard("Học sinh", "246", "Trong các lớp của bạn", "violet"), 1, 0);
        stats.add(statCard("Đề thi", "12", "03 đề đang mở", "orange"), 2, 0);
        stats.add(statCard("Chờ chấm", "18", "Bài nộp cần xem", "green"), 3, 0);
        for (Node node : stats.getChildren()) {
            GridPane.setHgrow(node, Priority.ALWAYS);
        }

        HBox panels = new HBox(18, recentExamsPanel(), classSummaryPanel());
        HBox.setHgrow(panels.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(panels.getChildren().get(1), Priority.ALWAYS);
        pageContent.getChildren().addAll(sampleNotice, stats, panels);
    }

    private VBox statCard(String title, String value, String note, String accent) {
        VBox card = new VBox(13);
        card.getStyleClass().addAll("teacher-stat-card", "accent-" + accent);
        Label caption = new Label(title.toUpperCase());
        caption.getStyleClass().add("teacher-stat-caption");
        Label number = new Label(value);
        number.getStyleClass().add("teacher-stat-number");
        Label detail = new Label(note);
        detail.getStyleClass().add("teacher-stat-detail");
        card.getChildren().addAll(caption, number, detail);
        card.setMaxWidth(Double.MAX_VALUE);
        GridPane.setFillWidth(card, true);
        return card;
    }

    private VBox recentExamsPanel() {
        VBox panel = panel("Đề thi gần đây", "Xem tất cả");
        panel.getChildren().addAll(
                examRow("Kiểm tra giữa kỳ · Toán 10A", "Hôm nay, 09:00", "ĐANG DIỄN RA", "status-live"),
                separator(),
                examRow("Bài tập chương 3 · Toán 10B", "Ngày mai, 14:30", "ĐÃ LÊN LỊCH", "status-planned"),
                separator(),
                examRow("Ôn tập đại số · Toán 11A", "25/09/2026", "ĐÃ KẾT THÚC", "status-done"));
        return panel;
    }

    private VBox classSummaryPanel() {
        VBox panel = panel("Lớp học của tôi", "Quản lý lớp");
        panel.getChildren().addAll(
                classRow("10A · Toán nâng cao", "36 học sinh", "10A"),
                separator(),
                classRow("10B · Toán cơ bản", "32 học sinh", "10B"),
                separator(),
                classRow("11A · Đại số", "29 học sinh", "11A"));
        return panel;
    }

    private void showClasses() {
        pageContent.getChildren().add(sampleNotice());
        pageContent.getChildren().addAll(
                sectionHeading("Danh sách lớp", "Quản lý lớp học và thành viên"),
                classCard("Toán nâng cao", "Lớp 10A", "36 học sinh", "Năm học 2026–2027"),
                classCard("Toán cơ bản", "Lớp 10B", "32 học sinh", "Năm học 2026–2027"),
                classCard("Đại số", "Lớp 11A", "29 học sinh", "Năm học 2026–2027"));
        pageContent.getChildren().add(actionButton("＋  Tạo lớp mới", "Tạo lớp mới"));
    }

    private void showExams() {
        pageContent.getChildren().add(sampleNotice());
        pageContent.getChildren().addAll(
                sectionHeading("Đề thi", "Tạo và theo dõi các kỳ kiểm tra"),
                examCard("Kiểm tra giữa kỳ", "Toán 10A", "20 câu hỏi", "Đang diễn ra"),
                examCard("Bài tập chương 3", "Toán 10B", "15 câu hỏi", "Đã lên lịch"),
                examCard("Ôn tập đại số", "Toán 11A", "25 câu hỏi", "Đã kết thúc"),
                actionButton("＋  Tạo đề thi", "Tạo đề thi"));
    }

    private void showQuestions() {
        pageContent.getChildren().addAll(
                sampleNotice(),
                sectionHeading("Ngân hàng câu hỏi", "Phân loại và quản lý câu hỏi của bạn"),
                statCard("Tổng số câu hỏi", "128", "Trắc nghiệm và tự luận", "blue"),
                classCard("Đại số", "64 câu hỏi", "12 chủ đề", "Cập nhật gần đây"),
                classCard("Hình học", "42 câu hỏi", "8 chủ đề", "Cập nhật tuần này"),
                classCard("Thống kê", "22 câu hỏi", "5 chủ đề", "Cập nhật tháng này"),
                actionButton("＋  Thêm câu hỏi", "Thêm câu hỏi"));
    }

    private void showResults() {
        pageContent.getChildren().addAll(
                sampleNotice(),
                sectionHeading("Kết quả học tập", "Theo dõi bài nộp và tiến độ của học sinh"),
                statCard("Bài đã nộp", "214", "Trong 7 ngày gần nhất", "violet"),
                classCard("Kiểm tra giữa kỳ · Toán 10A", "18 bài đã nộp", "02 bài chưa nộp", "Cập nhật hôm nay"),
                classCard("Bài tập chương 3 · Toán 10B", "28 bài đã nộp", "04 bài chưa nộp", "Hạn nộp ngày mai"),
                actionButton("Xem bảng điểm", "Xem bảng điểm"));
    }

    private HBox examRow(String title, String schedule, String status, String statusClass) {
        VBox details = new VBox(5, label(title, "teacher-row-title"), label(schedule, "teacher-row-subtitle"));
        Label badge = label(status, "teacher-status");
        badge.getStyleClass().add(statusClass);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, details, spacer, badge);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 0, 12, 0));
        return row;
    }

    private HBox classRow(String title, String students, String initials) {
        Label avatar = label(initials, "teacher-class-avatar");
        VBox details = new VBox(5, label(title, "teacher-row-title"), label(students, "teacher-row-subtitle"));
        HBox row = new HBox(12, avatar, details);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 0, 12, 0));
        return row;
    }

    private VBox classCard(String title, String course, String students, String note) {
        VBox card = new VBox(10,
                label(title, "teacher-card-title"),
                label(course + "  ·  " + students, "teacher-row-subtitle"),
                label(note, "teacher-row-subtitle"));
        card.getStyleClass().add("teacher-list-card");
        return card;
    }

    private VBox examCard(String title, String course, String questions, String status) {
        HBox header = new HBox(label(title, "teacher-card-title"), spacer());
        Label state = label(status, "teacher-row-subtitle");
        header.getChildren().add(state);
        VBox card = new VBox(11, header, label(course + "  ·  " + questions, "teacher-row-subtitle"));
        card.getStyleClass().add("teacher-list-card");
        return card;
    }

    private VBox panel(String title, String linkText) {
        VBox panel = new VBox(8);
        panel.getStyleClass().add("teacher-panel");
        HBox header = new HBox(label(title, "teacher-panel-title"), spacer());
        Button link = new Button(linkText);
        link.getStyleClass().add("teacher-link-button");
        link.setOnAction(event -> showPage(title.equals("Đề thi gần đây") ? "Đề thi" : "Lớp học"));
        header.getChildren().add(link);
        panel.getChildren().add(header);
        return panel;
    }

    private HBox sectionHeading(String title, String detail) {
        VBox texts = new VBox(5, label(title, "teacher-section-title"), label(detail, "teacher-row-subtitle"));
        return new HBox(texts);
    }

    private HBox sampleNotice() {
        HBox notice = new HBox(label("BẢN XEM TRƯỚC  ·  Nội dung minh họa, chưa lấy từ server", "teacher-notice-text"));
        notice.getStyleClass().add("teacher-sample-notice");
        return notice;
    }

    private Button actionButton(String text, String action) {
        Button button = new Button(text);
        button.getStyleClass().add("teacher-primary-button");
        button.setOnAction(event -> showPlaceholder(action));
        return button;
    }

    private void showPlaceholder(String action) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(action);
        alert.setHeaderText(action);
        alert.setContentText("Giao diện thao tác sẽ được kết nối với API quản lý dữ liệu ở bước tiếp theo.");
        alert.showAndWait();
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static Region separator() {
        Region line = new Region();
        line.getStyleClass().add("teacher-separator");
        line.setMinHeight(1);
        line.setMaxHeight(1);
        return line;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static String displayName(String email) {
        int at = email.indexOf('@');
        String localPart = at > 0 ? email.substring(0, at) : email;
        String readable = localPart.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim();
        return readable.isEmpty() ? "Giáo viên" : readable;
    }

    private static String initials(String email) {
        String name = displayName(email);
        String[] parts = name.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
