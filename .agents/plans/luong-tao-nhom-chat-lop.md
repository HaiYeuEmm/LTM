# Kế hoạch: Chat nhóm theo lớp học

## Mục tiêu

Sau khi giáo viên tạo lớp và thêm học sinh, hệ thống tự tạo một phòng chat chung cho lớp. Giáo viên và các học sinh đang là thành viên có thể trao đổi trong phòng đó.

**Giả định nghiệp vụ:** “nhóm” ở đây là lớp học do giáo viên sở hữu. Mỗi lớp có tối đa một phòng chat.

## Luồng chính

```mermaid
sequenceDiagram
    actor T as Giáo viên
    participant C as Client giáo viên
    participant S as Spring Boot Server
    participant D as MySQL
    participant U as Client học sinh

    T->>C: Tạo lớp
    C->>S: POST /api/classes
    S->>S: Xác thực role và quyền sở hữu
    S->>D: Lưu lớp
    D-->>S: classroomId
    S-->>C: Lớp mới
    T->>C: Thêm học sinh
    C->>S: POST /api/classes/{id}/members
    S->>S: Chuẩn hóa, kiểm tra toàn bộ danh sách
    alt Danh sách có lỗi
        S-->>C: Lỗi theo từng email; không ghi batch
    else Danh sách hợp lệ
        S->>D: BEGIN transaction
        S->>D: Thêm membership lớp
        S->>D: Tạo room nếu chưa có
        S->>D: Thêm giáo viên và học sinh vào room
        S->>D: COMMIT
        S-->>C: classroomId, roomId, số thành viên
        S-->>U: CHAT_ROOM_ADDED (nếu online)
        T->>C: Mở chat lớp
        C->>S: Tải lịch sử tin nhắn
    end
    T->>C: Gửi tin nhắn
    C->>S: CHAT_SEND hoặc POST message
    S->>S: Kiểm tra thành viên và nội dung
    S->>D: Lưu tin nhắn
    S-->>C: CHAT_MESSAGE_NEW
    S-->>U: CHAT_MESSAGE_NEW
```

### Quy tắc tạo phòng

- Tạo lớp chưa có học sinh thì chưa cần tạo phòng chat.
- Khi thêm thành công học sinh đầu tiên, Server tạo phòng chat lớp trong cùng transaction với membership.
- Ràng buộc unique trên `chat_rooms.classroom_id` bảo đảm retry không tạo phòng trùng.
- Các lần thêm học sinh tiếp theo chỉ đồng bộ thành viên vào phòng hiện có.
- Server thêm giáo viên sở hữu lớp làm quản trị viên phòng; không tin `teacherId` hoặc role do client gửi.

## Kiểm tra danh sách thành viên

- Chuẩn hóa email bằng `trim` và lowercase trước khi tra cứu.
- Chỉ tài khoản `STUDENT` đang `ACTIVE` mới được thêm.
- Báo lỗi rõ theo email: sai định dạng, không có tài khoản, sai role, bị vô hiệu hóa hoặc đã thuộc lớp.
- Kiểm tra hết batch trước khi ghi. Nếu có lỗi thì trả lỗi và không ghi một phần danh sách.
- Membership lớp và membership chat được tạo/cập nhật trong cùng transaction.

## Gửi và nhận tin nhắn

1. Client gửi `roomId`, nội dung và mã yêu cầu duy nhất; danh tính lấy từ access token, không lấy `senderId` do client khai báo.
2. Server kiểm tra tài khoản còn hoạt động và đang là thành viên phòng.
3. Nội dung chỉ là văn bản, tối đa 4.000 ký tự ở MVP; chưa cho đính kèm tệp.
4. Server lưu tin nhắn thành công rồi mới broadcast qua WebSocket/STOMP.
5. Khi mất kết nối, client tải lại lịch sử theo trang qua REST. Retry cùng mã yêu cầu không tạo tin nhắn trùng.
6. Tin nhắn mới gửi cho các thành viên online; thành viên offline nhận lại qua lịch sử khi reconnect.

## Rời lớp và quyền xem lịch sử

- Gỡ học sinh khỏi lớp đồng thời đặt membership chat thành `LEFT` trong cùng transaction.
- Người đã rời lớp không xem tin mới và không thể gửi tin; dữ liệu tin cũ không bị xóa.
- Thành viên mới chỉ được truy vấn tin có `created_at >= joined_at` của chính membership đó.
- Xóa mềm tin nhắn do giáo viên quản lý; mọi thao tác sửa/xóa cần lưu người thực hiện và thời điểm.

## Mô hình dữ liệu đề xuất

- `chat_rooms`: `id`, `classroom_id` (unique, foreign key), `name`, `created_by`, `created_at`.
- `chat_room_members`: `room_id`, `user_id`, `role`, `status`, `joined_at`, `left_at`; unique `(room_id, user_id)`.
- `chat_messages`: `id`, `room_id`, `sender_id`, `request_id` (unique theo sender), `content`, `created_at`, `edited_at`, `deleted_at`.
- Thêm foreign key, index `(room_id, created_at)` và index membership theo `user_id/status`.

## API và sự kiện

- `POST /api/classes/{classId}/members`: thêm thành viên và đảm bảo phòng chat tồn tại.
- `GET /api/classes/{classId}/chat`: lấy phòng chat nếu caller là thành viên.
- `GET /api/chat/rooms/{roomId}/messages?before=&limit=`: tải lịch sử phân trang, giới hạn quyền theo `joined_at`.
- `POST /api/chat/rooms/{roomId}/messages`: lưu tin nhắn, có idempotency key.
- WebSocket server event: `CHAT_ROOM_ADDED`, `CHAT_MEMBER_CHANGED`, `CHAT_MESSAGE_NEW`.
- Xác thực và phân quyền ở Server cho REST lẫn subscribe/send WebSocket.

## Điều kiện tiên quyết

- Hoàn thiện access token hoặc phiên đăng nhập có thời hạn. Luồng login hiện chỉ trả role để điều hướng giao diện, chưa cấp danh tính xác thực cho API chat.
- Có API tạo lớp và API thêm học sinh trước khi nối màn hình chat.
- Dùng HTTPS/WSS khi chạy qua mạng; không cho client truy cập MySQL trực tiếp.

## Tiêu chí nghiệm thu

- Thêm học sinh hợp lệ vào lớp mới sẽ tạo đúng một phòng chat và thêm giáo viên cùng học sinh vào phòng.
- Gửi lại yêu cầu thêm thành viên không nhân đôi membership hoặc phòng chat.
- Batch có email lỗi không ghi một phần và trả lỗi gắn với từng email.
- Người không thuộc lớp không xem lịch sử, gửi tin hoặc subscribe WebSocket được.
- Gỡ học sinh thu hồi quyền gửi/xem tin mới; lịch sử vẫn được giữ cho quản trị theo chính sách lưu trữ.
- Tin nhắn chỉ được broadcast sau khi Server lưu thành công; retry không tạo bản sao.
