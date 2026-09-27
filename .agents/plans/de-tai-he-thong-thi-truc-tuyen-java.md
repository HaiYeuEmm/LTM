# Đề tài: Hệ thống thi trực tuyến có giám sát – Java Ecosystem

## 1. Tóm tắt đề tài

Xây dựng hệ thống thi trực tuyến gồm hai ứng dụng desktop JavaFX (Giáo viên và Học sinh) cùng một dịch vụ Server trung gian. Giáo viên quản lý lớp, tạo đề và phiên thi; học sinh đăng nhập, tham gia phiên thi, làm bài trong giao diện toàn màn hình và nộp bài. Server cung cấp REST API cho dữ liệu nghiệp vụ, WebSocket qua TCP cho giám sát thời gian thực, lưu bài làm và kết quả vào cơ sở dữ liệu.

Ứng dụng hỗ trợ phát hiện một số dấu hiệu rời khỏi bài thi, ghi nhận cảnh báo và cho phép giáo viên tạm khóa/mở lại phiên của học sinh. Đây là hệ thống hỗ trợ giám sát: các sự kiện từ client cần được lưu như bằng chứng tham khảo, không nên tự động coi một cảnh báo đơn lẻ là kết luận gian lận.

## 2. Mục tiêu và phạm vi

### Mục tiêu

- Quản lý tài khoản, lớp học, danh sách học sinh, bộ câu hỏi và phiên thi.
- Tạo phòng chat cho lớp sau khi giáo viên thêm học sinh vào lớp.
- Cho phép học sinh tham gia bài thi được giáo viên phát hành.
- Lưu bài làm định kỳ ở máy học sinh và đồng bộ lên Server để giảm nguy cơ mất dữ liệu.
- Gửi trạng thái online, cảnh báo và lệnh điều khiển phiên thi gần thời gian thực.
- Chấm điểm câu hỏi khách quan sau khi nộp và lưu bảng điểm, thời gian làm, số cảnh báo.
- Ghi nhật ký hoạt động để giáo viên có thể xem lại quá trình thi.

### Phạm vi phiên bản đầu

- Hỗ trợ câu hỏi trắc nghiệm một đáp án; có thể mở rộng sang nhiều đáp án, đúng/sai và tự luận.
- Hỗ trợ một Server trung tâm chạy trong mạng LAN hoặc trên máy chủ riêng.
- Hỗ trợ các sự kiện giám sát khả thi ở tầng ứng dụng: mất focus, thoát toàn màn hình, mất kết nối, trạng thái màn hình phụ nếu hệ điều hành cung cấp.
- Có chế độ tạm khóa giao diện bài thi và cho phép giáo viên mở lại.

### Ngoài phạm vi cam kết của phiên bản đầu

- Không cam kết chặn tuyệt đối Alt+Tab, phím Windows, Task Manager hay phần mềm chạy ngoài ứng dụng. JavaFX/JNativeHook không biến ứng dụng thường thành kiosk bảo mật của hệ điều hành; cần thiết lập kiosk, chính sách thiết bị hoặc phần mềm quản lý máy riêng nếu yêu cầu khóa ở cấp hệ thống.
- Không coi việc quét tên tiến trình là phát hiện đáng tin cậy: tên có thể bị đổi, quyền truy cập bị hạn chế và kết quả khác nhau theo hệ điều hành. Nếu triển khai, chỉ ghi nhận tín hiệu và công khai danh sách kiểm tra.
- Không truyền webcam hoặc ảnh màn hình liên tục trong MVP. Chỉ bổ sung khi có căn cứ nghiệp vụ, thông báo rõ cho người dự thi, giới hạn quyền truy cập và thời hạn lưu.

## 3. Kiến trúc tổng thể

```text
+--------------------------+       HTTPS / REST       +--------------------------+
| Desktop Giáo viên        | <----------------------> |                          |
| JavaFX                   |                          | Java Server              |
| Dashboard, quản lý lớp,  |       WSS / WebSocket    | Spring Boot              |
| đề thi, giám sát, điểm   | <======================> | REST API + Realtime      |
+--------------------------+                          |                          |
                                                       | PostgreSQL / MySQL       |
+--------------------------+       HTTPS / REST       |                          |
| Desktop Học sinh         | <----------------------> |                          |
| JavaFX                   |       WSS / WebSocket    +--------------------------+
| Làm bài, autosave, cảnh  |
| báo, nộp bài             |
+--------------------------+
```

### Thành phần

1. **Client Giáo viên (JavaFX):** đăng nhập; quản lý lớp, thành viên và chat lớp; tạo đề, đáp án và lịch thi; theo dõi trạng thái; nhận cảnh báo; gửi lệnh khóa/mở khóa; xem kết quả.
2. **Client Học sinh (JavaFX):** đăng nhập; chat với thành viên lớp được cấp quyền; tải bài thi được phép tham gia; làm bài; lưu cục bộ; đồng bộ bản nháp; gửi heartbeat và sự kiện giám sát; nộp bài.
3. **Server (Spring Boot):** xác thực và phân quyền; REST API; WebSocket/STOMP hoặc WebSocket thuần; xử lý chat, lệnh và sự kiện; nhận autosave và bài nộp; chấm điểm phía Server; truy cập cơ sở dữ liệu.
4. **Cơ sở dữ liệu (PostgreSQL khuyến nghị):** lưu người dùng, lớp, thành viên, đề, câu hỏi, phiên thi, bài làm, cảnh báo và điểm.

Máy giáo viên có thể chạy Server trong mạng LAN cho bản trình diễn, nhưng về thiết kế vẫn xem Server là thành phần riêng. Client kết nối qua địa chỉ cấu hình; không để máy giáo viên truy cập trực tiếp cơ sở dữ liệu từ ứng dụng desktop.

## 4. Công nghệ đề xuất

| Lớp | Công nghệ | Vai trò |
|---|---|---|
| Desktop UI | Java 17+ / JavaFX / FXML | Hai ứng dụng Giáo viên và Học sinh |
| Backend | Java 17+ / Spring Boot | REST API, xác thực, nghiệp vụ |
| Realtime | Spring WebSocket (WSS) | Heartbeat, cảnh báo, lệnh khóa/mở khóa, cập nhật trạng thái |
| ORM | Spring Data JPA / Hibernate | Truy cập dữ liệu |
| Database | PostgreSQL (hoặc MySQL) | Dữ liệu nghiệp vụ và nhật ký |
| HTTP client | `java.net.http.HttpClient` | Gọi REST từ desktop client |
| Build | Maven hoặc Gradle | Build và quản lý dependency |
| Kiểm thử | JUnit, Spring Boot Test | Kiểm thử nghiệp vụ và API |

JNativeHook chỉ nên được đánh giá như một nguồn phát hiện/phản hồi bàn phím, không phải ranh giới bảo mật. Không dùng UDP để truyền dữ liệu cần đảm bảo đến nơi, đúng thứ tự hoặc cần lưu vết; các luồng đó dùng HTTPS/WSS qua TCP.

## 5. Phân bổ giao thức mạng

| Giao thức | Dữ liệu | Ghi chú |
|---|---|---|
| HTTPS REST | Đăng nhập, CRUD lớp/đề, tải đề, autosave, nộp bài, truy vấn điểm | Dữ liệu nghiệp vụ có phản hồi, xác thực và khả năng retry rõ ràng |
| WSS (WebSocket trên TLS/TCP) | Heartbeat, online/offline, cảnh báo, lệnh ban/unban, trạng thái phiên | Dùng mã sự kiện, timestamp, session ID; kiểm tra quyền cho từng lệnh |
| UDP (tùy chọn, giai đoạn sau) | Có thể dùng cho media thời gian thực nếu thật sự cần | Không dùng cho điểm, bài làm hoặc lệnh điều khiển; phải thiết kế mã hóa, xác thực, đồng ý và chính sách lưu trữ riêng |

Không cần triển khai `DatagramSocket` chỉ vì tốc độ. Ảnh màn hình gửi định kỳ tạo rủi ro riêng tư và tải mạng; nếu có yêu cầu, nên cân nhắc WebRTC/giải pháp truyền media phù hợp, giới hạn tần suất, độ phân giải, người được xem và thời hạn xóa.

## 6. Vai trò và quyền

- **Giáo viên:** quản lý lớp thuộc quyền; tạo/phát hành đề; mở/đóng phiên; theo dõi thí sinh; gửi lệnh tạm khóa/mở lại; xem cảnh báo và kết quả.
- **Học sinh:** chỉ xem lớp/bài thi được cấp quyền; chỉ gửi bài làm của chính mình; không được truy cập đáp án chuẩn trước khi phiên kết thúc.
- **Quản trị viên (tùy chọn):** quản lý tài khoản và cấu hình hệ thống.

Server là nguồn có thẩm quyền cho danh tính, quyền tham gia, thời gian bắt đầu/kết thúc, trạng thái phiên và điểm chính thức. Client không được tự quyết định quyền mở bài thi hoặc kết quả cuối.

## 7. Luồng nghiệp vụ chi tiết

### 7.1 Giáo viên chuẩn bị

1. Giáo viên đăng nhập; Server xác thực và trả access token có thời hạn.
2. Giáo viên tạo lớp hoặc chọn lớp hiện có.
3. Giáo viên nhập danh sách học sinh (CSV/email) và gửi lời mời hoặc gán tài khoản.
4. Giáo viên tạo bộ đề: câu hỏi, lựa chọn, đáp án chuẩn, điểm và thứ tự.
5. Giáo viên tạo phiên thi, thiết lập thời gian, lớp được phép tham gia, quy tắc làm bài và thời điểm mở/đóng.
6. Khi phát hành, Server kiểm tra dữ liệu, lưu phiên ở trạng thái `SCHEDULED`/`OPEN` và thông báo cho client liên quan.

### 7.6 Tạo chat lớp và đồng bộ thành viên

1. Giáo viên tạo lớp và thêm một hoặc nhiều tài khoản học sinh đang hoạt động.
2. Server xác thực giáo viên sở hữu lớp, chuẩn hóa email, kiểm tra tài khoản và thành viên trùng; nếu có lỗi, trả lỗi theo từng học sinh và không ghi một phần batch.
3. Khi thêm học sinh hợp lệ đầu tiên, Server tạo duy nhất một phòng chat gắn với lớp. Trong cùng transaction, Server thêm giáo viên sở hữu lớp và các học sinh mới vào danh sách thành viên chat.
4. Server commit dữ liệu trước rồi mới gửi `CHAT_ROOM_ADDED` qua WebSocket cho thành viên đang online. Client tải danh sách phòng qua REST khi đăng nhập hoặc reconnect.
5. Khi gửi tin nhắn, Server xác thực người gửi còn là thành viên, lưu tin nhắn rồi mới broadcast `CHAT_MESSAGE_NEW`. Tin nhắn offline được tải theo trang khi thành viên mở lại phòng.
6. Khi học sinh bị gỡ khỏi lớp, quyền vào chat bị thu hồi ngay; lịch sử vẫn được giữ. Học sinh mới chỉ xem tin nhắn từ thời điểm tham gia để tránh lộ lịch sử trước khi vào lớp.

Chi tiết luồng, mô hình dữ liệu, quyền và tiêu chí nghiệm thu nằm trong [luồng chat lớp học](luong-tao-nhom-chat-lop.md).

### 7.2 Học sinh vào thi

1. Học sinh đăng nhập; Server xác thực danh tính, trạng thái tài khoản và thành viên lớp.
2. Client tải danh sách phiên thi khả dụng. Học sinh chọn phiên được mở.
3. Server kiểm tra quyền, thời gian và số lần tham gia; tạo hoặc khôi phục bản ghi bài làm.
4. Client tải nội dung câu hỏi nhưng không tải đáp án chuẩn.
5. Client chuyển giao diện thi sang toàn màn hình, bắt đầu đồng hồ dựa trên mốc thời gian Server và mở WSS.
6. Client gửi `STUDENT_ONLINE`/heartbeat. Server đánh dấu phiên kết nối và đưa học sinh vào dashboard.

### 7.3 Làm bài, autosave và giám sát

1. Mỗi thay đổi câu trả lời được ghi vào lưu trữ cục bộ (ví dụ SQLite hoặc tệp mã hóa) với mã phiên, phiên bản và thời điểm.
2. Theo chu kỳ hoặc sau một nhóm thay đổi, client gửi bản cập nhật lên REST API. Gửi theo lô, có `clientSequence`/`idempotencyKey` để retry không tạo bản ghi trùng.
3. Server lưu bản nháp và trả về phiên bản đã xác nhận. Khi mạng trở lại, client đồng bộ các thay đổi chưa xác nhận.
4. WebSocket duy trì heartbeat và gửi sự kiện như `FOCUS_LOST`, `FULLSCREEN_EXIT`, `DISPLAY_CHANGED`, `RESTRICTED_PROCESS_DETECTED` (nếu được bật).
5. Server ghi sự kiện vào nhật ký, tăng bộ đếm cảnh báo và đẩy thông báo lên dashboard giáo viên.
6. Sự kiện phải có `eventId`, `examSessionId`, `studentId`, loại sự kiện, thời điểm client, thời điểm server và metadata tối thiểu. Server chống gửi lặp và lưu cả thời điểm nhận.

### 7.4 Tạm khóa và cho phép làm tiếp

1. Giáo viên chọn học sinh và bấm **Tạm khóa** hoặc **Gỡ khóa / Cho phép làm tiếp** trên dashboard.
2. Client giáo viên gửi lệnh đến Server; Server kiểm tra giáo viên có quyền trên lớp/phiên đó.
3. Server lưu lệnh và trạng thái mới (`LOCKED`/`ACTIVE`) trước khi phát lệnh qua WSS đến client học sinh.
4. Client học sinh nhận lệnh, phủ màn hình thi bằng trạng thái tạm khóa hoặc mở lại bài thi; gửi ACK kèm `commandId`.
5. Nếu học sinh offline, Server giữ trạng thái/lệnh chờ; khi reconnect client hỏi trạng thái phiên mới nhất và áp dụng trạng thái từ Server.
6. Dashboard hiển thị đã gửi/đã nhận/chưa xác nhận. Việc tạm khóa không tự xóa câu trả lời và không đồng nghĩa với kết luận gian lận.

### 7.5 Nộp bài và chấm điểm

1. Học sinh bấm nộp hoặc hết giờ. Client khóa thao tác chỉnh sửa và gửi bài cùng `submissionId`, phiên bản cuối, thời gian bắt đầu/kết thúc và danh sách câu trả lời.
2. Server kiểm tra quyền, hạn nộp, phiên bản và tính duy nhất của lần nộp; lưu bản nộp bất biến.
3. Server chấm các câu khách quan dựa trên đáp án chuẩn phía Server. Không tin điểm do client tự gửi.
4. Server lưu điểm, thời gian làm, số cảnh báo, trạng thái nộp; trả biên nhận cho học sinh và cập nhật dashboard giáo viên.
5. Khi mất mạng lúc nộp, client lưu trạng thái chờ gửi và retry với cùng `submissionId`; giao diện chỉ báo nộp thành công sau khi có biên nhận Server.

## 8. Máy trạng thái

### Phiên thi

`DRAFT → SCHEDULED → OPEN → CLOSED → GRADED`

Các chuyển trạng thái do Server kiểm soát và ghi nhật ký.

### Bài làm của học sinh

`NOT_STARTED → IN_PROGRESS ↔ LOCKED → SUBMITTED → GRADED`

Mất kết nối là trạng thái kết nối riêng (`ONLINE`/`OFFLINE`), không tự làm mất bài hoặc đổi trạng thái nộp.

## 9. Mô hình dữ liệu chính

- `User(id, email, passwordHash, role, status)`
- `Classroom(id, name, ownerTeacherId)`
- `ClassMembership(classroomId, studentId, status)`
- `ChatRoom(id, classroomId, name, createdAt)` — tối đa một phòng chat cho mỗi lớp.
- `ChatRoomMember(roomId, userId, joinedAt, leftAt, status)`
- `ChatMessage(id, roomId, senderId, content, createdAt, editedAt, deletedAt)`
- `Exam(id, title, duration, settings, status)`
- `Question(id, examId, type, content, points, position)`
- `Choice(id, questionId, content)`
- `AnswerKey(questionId, correctChoiceIds)` — chỉ Server/giáo viên được truy cập trước khi công bố điểm.
- `ExamSession(id, examId, classroomId, opensAt, closesAt, status)`
- `Attempt(id, sessionId, studentId, state, startedAt, submittedAt, version)`
- `Answer(attemptId, questionId, response, updatedAt, version)`
- `ProctorEvent(id, attemptId, eventType, clientTime, serverTime, metadata)`
- `Command(id, attemptId, commandType, actorId, createdAt, acknowledgedAt)`
- `Result(attemptId, score, durationSeconds, warningCount, gradedAt)`

Lược đồ thực tế cần ràng buộc khóa ngoại, chỉ mục theo phiên/lớp/học sinh và chính sách lưu/xóa dữ liệu.

## 10. API và sự kiện tham khảo

### REST API

- `POST /api/auth/login`
- `GET /api/classes` / `POST /api/classes`
- `POST /api/classes/{id}/members/import`
- `GET /api/classes/{id}/chat`
- `GET /api/chat/rooms/{roomId}/messages?before=&limit=`
- `POST /api/chat/rooms/{roomId}/messages`
- `POST /api/exams` / `GET /api/exams/{id}`
- `POST /api/sessions` / `POST /api/sessions/{id}/open`
- `POST /api/sessions/{id}/attempts`
- `PUT /api/attempts/{id}/answers` (autosave, có version/idempotency key)
- `POST /api/attempts/{id}/submit`
- `GET /api/sessions/{id}/results`
- `POST /api/attempts/{id}/commands` (tạm khóa/mở khóa, có kiểm tra quyền)

### WebSocket event

- Client → Server: `HEARTBEAT`, `STUDENT_ONLINE`, `PROCTOR_EVENT`, `COMMAND_ACK`, `ATTEMPT_STATE`.
- Server → client: `SESSION_STATE`, `PROCTOR_ALERT`, `LOCK_ATTEMPT`, `UNLOCK_ATTEMPT`, `SESSION_CLOSED`.
- Chat: Server → thành viên hợp lệ `CHAT_ROOM_ADDED`, `CHAT_MEMBER_CHANGED`, `CHAT_MESSAGE_NEW`.

Mỗi thông điệp có `messageId`, `type`, `sessionId`, `attemptId`, `sentAt` và `payload`. Kiểm tra schema, kích thước và quyền ở Server; không cho client tự chọn `studentId` làm căn cứ quyền.

## 11. Bảo mật, quyền riêng tư và độ tin cậy

- Dùng HTTPS/WSS, lưu mật khẩu dạng hash an toàn (Argon2 hoặc BCrypt), token ngắn hạn và phân quyền theo vai trò/lớp/phiên.
- Không đóng gói đáp án chuẩn vào ứng dụng học sinh; chấm điểm chính thức ở Server.
- Kiểm tra đầu vào, giới hạn tần suất, kích thước payload, thời gian phiên và quyền truy cập mọi API/lệnh.
- Dùng idempotency key và version để xử lý retry, mất kết nối và xung đột autosave.
- Ghi audit log cho thay đổi đề, mở/đóng phiên, khóa/mở khóa, chấm điểm và truy cập kết quả.
- Chỉ thu thập tín hiệu giám sát cần thiết; hiển thị trước cho học sinh loại dữ liệu được thu, mục đích, người có thể xem và thời hạn lưu.
- Mã hóa dữ liệu cục bộ nếu lưu câu trả lời trên thiết bị dùng chung; xóa bản nháp cục bộ theo chính sách sau khi đồng bộ/nộp.
- Không xem cảnh báo client là bằng chứng chắc chắn: client có thể bị đóng, mất mạng hoặc bị sửa đổi. Hiển thị nguồn và trạng thái xác nhận để giáo viên đánh giá.
- Với nhu cầu khóa thiết bị nghiêm ngặt, triển khai trên máy do tổ chức quản lý bằng kiosk mode/chính sách hệ điều hành; không quảng bá JavaFX/JNativeHook như cơ chế chống gian lận tuyệt đối.

## 12. Giao diện dự kiến

### Giáo viên

- Đăng nhập.
- Danh sách lớp và quản lý thành viên.
- Chat theo lớp, danh sách thành viên và lịch sử tin nhắn.
- Trình tạo đề và cấu hình phiên thi.
- Dashboard phiên thi: học sinh, trạng thái online, tiến độ, cảnh báo, trạng thái khóa.
- Chi tiết học sinh: dòng thời gian sự kiện, câu trả lời đã lưu, lệnh khóa/mở và trạng thái ACK.
- Bảng kết quả có điểm, thời gian làm, số cảnh báo và trạng thái nộp.

### Học sinh

- Đăng nhập và danh sách bài thi được phép tham gia.
- Danh sách phòng chat của các lớp đang tham gia và gửi/nhận tin nhắn.
- Màn hình hướng dẫn/quy tắc và xác nhận bắt đầu.
- Giao diện làm bài, đồng hồ, điều hướng câu, trạng thái đồng bộ.
- Màn hình tạm khóa có hướng dẫn liên hệ giáo viên.
- Xác nhận nộp và biên nhận nộp bài.

## 13. Kế hoạch triển khai theo giai đoạn

1. **Giai đoạn 1 – Nền tảng:** repository/module, cấu hình, database, xác thực, phân quyền, CRUD lớp và người dùng.
2. **Giai đoạn 2 – Đề và phiên thi:** tạo đề, phát hành phiên, tham gia thi và giao diện làm trắc nghiệm.
3. **Giai đoạn 3 – Bài làm tin cậy:** autosave cục bộ/Server, retry, nộp bài idempotent, chấm điểm Server.
4. **Giai đoạn 4 – Realtime và chat lớp:** WebSocket, heartbeat, chat theo lớp, online/offline, cảnh báo, dashboard và lệnh khóa/mở có ACK.
5. **Giai đoạn 5 – Hoàn thiện:** audit, xuất kết quả, xử lý lỗi, kiểm thử tải cơ bản, đóng gói cài đặt và hướng dẫn vận hành LAN.
6. **Giai đoạn tùy chọn – Giám sát nâng cao:** đánh giá riêng việc phát hiện ứng dụng/màn hình phụ hoặc truyền media; chỉ triển khai sau khi chốt quyền riêng tư, nền tảng và mô hình đe dọa.

## 14. Tiêu chí nghiệm thu gợi ý

- Giáo viên tạo lớp, nhập thành viên, tạo đề và mở phiên thi.
- Học sinh chỉ tham gia phiên được cấp quyền và trong thời gian hợp lệ.
- Câu trả lời được autosave; sau khi ngắt mạng rồi kết nối lại, bản nháp được đồng bộ mà không nhân đôi dữ liệu.
- Cảnh báo hiển thị trên dashboard; lệnh tạm khóa/mở khóa được xác nhận hoặc thể hiện rõ chưa tới client.
- Nộp lặp do retry không tạo nhiều bài nộp; điểm chính thức do Server tính từ đáp án lưu phía Server.
- Giáo viên xem được điểm, thời gian làm, số cảnh báo và lịch sử thao tác.
- Thêm học sinh vào lớp sẽ tạo/gắn đúng một phòng chat; thành viên ngoài lớp không đọc hoặc gửi được tin nhắn.
- Các giới hạn của phát hiện/khóa ở tầng desktop được nêu rõ trong tài liệu sử dụng.

## 15. Sơ đồ tuần tự tóm tắt

```text
Giáo viên             Server                 Học sinh
    |                    |                       |
    |-- tạo lớp/đề ------>|                       |
    |-- mở phiên -------->|                       |
    |                    |<-- đăng nhập/tham gia -|
    |                    |--- đề không có đáp án ->|
    |                    |<-- heartbeat/events ---|
    |<-- cảnh báo --------|                       |
    |-- lệnh khóa/mở ---->|--- WSS command ------->|
    |                    |<-- command ACK --------|
    |                    |<-- autosave ------------|
    |                    |<-- submit --------------|
    |                    |-- chấm điểm Server      |
    |<-- kết quả ---------|                       |
```

### Luồng tạo lớp chat

```text
Giáo viên                 Server / Database                Học sinh
    |                              |                            |
    |-- tạo lớp ------------------>|                            |
    |-- thêm danh sách học sinh -->|                            |
    |                              |-- kiểm tra quyền/danh sách |
    |                              |-- transaction:             |
    |                              |   thêm thành viên lớp      |
    |                              |   tạo/tìm phòng chat       |
    |                              |   thêm thành viên chat     |
    |<-- lớp + roomId -------------|                            |
    |                              |-- CHAT_ROOM_ADDED -------->| online
    |-- mở phòng chat ------------>|                            |
    |-- gửi tin nhắn ------------>|-- lưu rồi broadcast ------>|
    |<----------------------------|<---------------------------|
```

## 16. Kết luận

Đề tài tập trung vào một quy trình thi có quản lý và lưu vết: desktop client cung cấp trải nghiệm riêng cho giáo viên và học sinh; Spring Boot giữ quyền quyết định nghiệp vụ; HTTPS/WSS vận chuyển dữ liệu và sự kiện; cơ sở dữ liệu lưu phiên thi, bài làm, cảnh báo và kết quả. Tính toàn vẹn bài thi đến từ xác thực, phân quyền, lưu trữ Server, chấm điểm phía Server và audit log. Các chức năng lockdown chỉ là lớp hỗ trợ và cần được triển khai cùng chính sách quản lý thiết bị nếu cần mức cưỡng chế cao.
