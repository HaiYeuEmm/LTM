# Quy tắc nghiệp vụ – Hệ thống thi trực tuyến Java

Tài liệu này quy định hành vi nghiệp vụ thống nhất cho Client Giáo viên, Client Học sinh và Server. Các quyết định về quyền, trạng thái bài thi, dữ liệu đã nộp và điểm chính thức phải được Server xác thực và lưu trữ.

## 1. Vai trò và phân quyền

- Hệ thống có các vai trò `TEACHER`, `STUDENT` và tùy chọn `ADMIN`.
- Giáo viên chỉ quản lý lớp, đề và phiên thi thuộc quyền của mình.
- Học sinh chỉ xem lớp, đề và phiên thi mà tài khoản được cấp quyền tham gia.
- Mọi API và lệnh realtime phải xác thực người dùng và kiểm tra quyền ở Server. Không tin `studentId`, `teacherId` hoặc role do client tự gửi.
- Đáp án chuẩn không được gửi tới ứng dụng học sinh trước khi được phép công bố.

## 2. Lớp học và thành viên

- Thành viên lớp được thêm bởi giáo viên có quyền hoặc quản trị viên.
- Email cần được chuẩn hóa (trim và lowercase) trước khi kiểm tra trùng.
- Nhập danh sách phải báo rõ email không hợp lệ, trùng lặp và tài khoản chưa có trong hệ thống; không âm thầm bỏ qua bản ghi lỗi.
- Học sinh bị gỡ khỏi lớp không được bắt đầu phiên mới của lớp đó. Bài làm đã phát sinh vẫn được giữ theo chính sách lưu trữ.

## 3. Đề thi và phiên thi

- Đề thi và phiên thi là hai thực thể khác nhau: đề chứa câu hỏi/đáp án; phiên gắn đề với lớp, khung giờ và chính sách thi.
- Chỉ giáo viên có quyền được tạo, sửa và phát hành đề hoặc phiên thi.
- Trạng thái phiên: `DRAFT → SCHEDULED → OPEN → CLOSED → GRADED`.
- Chỉ Server được chuyển trạng thái phiên. Client chỉ yêu cầu chuyển trạng thái.
- Khi phiên đã bắt đầu có bài làm, nội dung đề và đáp án phải được đóng băng cho phiên đó hoặc tạo phiên bản đề mới. Không sửa ngược làm thay đổi kết quả của lượt thi đang diễn ra.
- Server là nguồn thời gian chính thức; đồng hồ client chỉ để hiển thị.
- Học sinh chỉ bắt đầu trong khoảng thời gian được cấu hình và khi phiên ở trạng thái `OPEN`.

## 4. Bài làm và thời gian

- Mỗi học sinh có một bài làm (`Attempt`) cho mỗi phiên, trừ khi giáo viên cho phép tạo lượt thi lại.
- Server ghi thời điểm bắt đầu và quyết định hạn nộp. Hạn cá nhân là thời điểm sớm hơn giữa hạn đóng phiên và thời điểm bắt đầu cộng thời lượng bài thi.
- Trạng thái bài làm: `NOT_STARTED → IN_PROGRESS ↔ LOCKED → SUBMITTED → GRADED`.
- Trạng thái kết nối `ONLINE`/`OFFLINE` được theo dõi độc lập, không tự đổi trạng thái bài làm.
- Thoát ứng dụng, mất focus hoặc mất mạng không được tự động xóa câu trả lời hay kết luận học sinh gian lận.
- Chỉ cho thi lại hoặc mở lại bài đã nộp khi có quyền giáo viên; mọi thao tác phải được ghi audit log.

## 5. Autosave và đồng bộ

- Client ghi thay đổi câu trả lời vào lưu trữ cục bộ trước khi đồng bộ để giảm nguy cơ mất dữ liệu khi mất mạng.
- Client gửi bản nháp lên Server theo chu kỳ hoặc sau một nhóm thay đổi; không gửi toàn bộ bài liên tục nếu chỉ có một câu thay đổi.
- Mỗi lần ghi có `attemptId`, số phiên bản và `idempotencyKey`/mã yêu cầu duy nhất. Retry cùng mã không được tạo dữ liệu trùng.
- Server xác nhận phiên bản đã lưu. Client chỉ đánh dấu “đã đồng bộ” sau xác nhận này; nếu offline, đánh dấu rõ bản thay đổi còn chờ gửi.
- Khi phiên bản không khớp, Server từ chối ghi đè mù và trả thông tin để client đồng bộ lại theo phiên bản mới nhất.

## 6. Giám sát và cảnh báo

- Các tín hiệu hỗ trợ gồm mất focus, thoát toàn màn hình, thay đổi màn hình, heartbeat quá hạn và (nếu được bật) phát hiện tiến trình hạn chế.
- Mỗi sự kiện có mã duy nhất, `attemptId`, loại sự kiện, thời điểm client, thời điểm Server nhận và metadata tối thiểu cần thiết.
- Server chống xử lý trùng sự kiện và lưu sự kiện vào lịch sử phiên thi.
- Sự kiện từ client là tín hiệu tham khảo, không phải bằng chứng tuyệt đối hoặc kết luận gian lận tự động.
- Mặc định, cảnh báo không tự động nộp bài, trừ điểm hoặc khóa vĩnh viễn. Hành động xử lý do giáo viên thực hiện theo quy định thi.
- Nếu bật quét tiến trình, phải công khai danh sách/loại dữ liệu được kiểm tra. Không coi tên tiến trình là bằng chứng chắc chắn vì có thể sai hoặc bị giả mạo.
- Khóa phím và toàn màn hình ở JavaFX/JNativeHook chỉ là biện pháp hỗ trợ. Không cam kết chặn tuyệt đối phím hệ thống hoặc ứng dụng khác; yêu cầu kiosk phải dựa vào chính sách hệ điều hành/thiết bị được quản lý.

## 7. Tạm khóa và mở khóa bài thi

- “Ban” trong nghiệp vụ này được hiểu là **tạm khóa bài làm**, không phải vô hiệu hóa tài khoản.
- Giáo viên được quyền gửi lệnh `LOCK` hoặc `UNLOCK` cho bài làm thuộc phiên/lớp mình quản lý.
- Server xác thực quyền, lưu trạng thái/lệnh trước khi phát qua WebSocket.
- Client học sinh khi nhận `LOCK` phải ngăn chỉnh sửa câu trả lời và hiển thị hướng dẫn liên hệ giáo viên; không xóa dữ liệu và mặc định không tự nộp bài.
- Client gửi ACK theo `commandId`; dashboard phải thể hiện lệnh đã xác nhận, đang chờ hay thất bại.
- Khi học sinh reconnect, client tải trạng thái chính thức từ Server và áp dụng trạng thái mới nhất, kể cả lệnh khóa/mở gửi lúc offline.
- Quy tắc đồng hồ khi bị khóa phải được cấu hình rõ. Mặc định đề xuất: đồng hồ vẫn chạy; giáo viên có thể gia hạn thời gian nếu cần và thao tác gia hạn phải được ghi log.

## 8. Nộp bài và chấm điểm

- Nộp bài gửi `submissionId` duy nhất, phiên bản câu trả lời cuối và metadata cần thiết.
- Server kiểm tra quyền, trạng thái phiên, hạn nộp và tính duy nhất trước khi nhận bài.
- Nộp lại cùng `submissionId` phải trả cùng biên nhận, không tạo bài nộp mới.
- Khi được Server xác nhận, bài nộp là bất biến. Mở lại/chỉnh sửa sau nộp chỉ thực hiện qua thao tác giáo viên được phân quyền và ghi audit log.
- Server chấm câu hỏi khách quan từ đáp án chuẩn phía Server; không tin điểm tự chấm hoặc đáp án chuẩn do client gửi.
- Kết quả gồm tối thiểu: điểm, thời gian làm, trạng thái nộp và số sự kiện/cảnh báo theo quy tắc đếm đã công bố.
- Câu tự luận cần trạng thái chấm riêng; không tự chấm bằng đáp án trắc nghiệm.
- Client chỉ báo nộp thành công sau khi nhận biên nhận Server. Nếu mất mạng, giữ trạng thái chờ gửi và retry cùng `submissionId`.

## 9. Quy tắc giao thức

- HTTPS REST dùng cho đăng nhập, CRUD, tải dữ liệu, autosave, nộp bài và truy vấn kết quả.
- WSS (WebSocket qua TLS/TCP) dùng cho heartbeat, trạng thái online, cảnh báo và lệnh khóa/mở.
- Thông điệp realtime phải có `messageId`, loại sự kiện, session/attempt liên quan và thời điểm gửi; Server kiểm tra schema, kích thước và quyền.
- UDP không dùng cho đáp án, bài làm, điểm hoặc lệnh điều khiển. Truyền webcam/ảnh màn hình là tính năng tùy chọn, cần được phê duyệt về mục đích, đồng ý, bảo mật, quyền xem và thời hạn xóa trước khi triển khai.

## 10. Bảo mật, riêng tư và nhật ký

- Kết nối triển khai thực tế phải dùng HTTPS/WSS; mật khẩu lưu dưới dạng hash an toàn (BCrypt hoặc Argon2), không lưu mật khẩu thô.
- API phải kiểm tra dữ liệu đầu vào, quyền truy cập, giới hạn kích thước và tần suất yêu cầu.
- Ghi audit log tối thiểu cho: phát hành/đóng phiên, sửa đề, khóa/mở khóa, gia hạn, mở lại bài, chấm điểm và xem/xuất kết quả.
- Chỉ thu thập dữ liệu giám sát cần thiết; thông báo cho học sinh về dữ liệu thu thập, mục đích, người có quyền xem và thời gian lưu.
- Có chính sách lưu/xóa cho bài làm, sự kiện, log và dữ liệu cục bộ. Dữ liệu local phải được bảo vệ phù hợp với thiết bị dùng chung.
- Server là nguồn chính thức cho danh tính, quyền, trạng thái phiên, câu trả lời đã xác nhận và điểm.

## 11. Mặc định nghiệp vụ đề xuất cho MVP

- Một lượt thi mỗi học sinh trong mỗi phiên; giáo viên có quyền mở lượt mới.
- Chỉ câu hỏi trắc nghiệm một đáp án được chấm tự động.
- Autosave cục bộ ngay khi thay đổi và đồng bộ Server định kỳ/theo lô.
- Mất mạng không xóa bài; client đồng bộ lại khi kết nối phục hồi.
- Cảnh báo chỉ hiển thị cho giáo viên, không tự động trừ điểm hoặc kết luận gian lận.
- Tạm khóa không xóa bài; đồng hồ tiếp tục chạy cho đến khi giáo viên gia hạn.
- Điểm chính thức được Server tính sau khi nhận bài nộp.
- Không truyền webcam/ảnh màn hình và không quét tiến trình trong MVP.

## 12. Các chính sách cần xác nhận trước khi phát hành

Các mục sau cần được cấu hình hoặc thống nhất với đơn vị tổ chức thi, không nên để mỗi client tự quyết:

1. Cho phép thi lại bao nhiêu lần và ai có quyền cấp lượt mới?
2. Khi tạm khóa, đồng hồ có dừng không? Mặc định MVP: không dừng.
3. Bao lâu không có heartbeat thì hiển thị offline?
4. Học sinh có được xem điểm/đáp án ngay sau khi nộp hay sau khi phiên đóng?
5. Thời hạn lưu bài làm, cảnh báo, audit log và dữ liệu cục bộ là bao lâu?
6. Có cần quản lý máy theo kiosk mode để thực thi lockdown ở cấp hệ điều hành không?
7. Có thực sự cần ảnh màn hình/webcam; nếu có thì ai xem, lưu bao lâu và cơ chế đồng ý là gì?
