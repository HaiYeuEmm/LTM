package vn.onlineexam.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> register(
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(defaultValue = "false") boolean termsAccepted) {
        String normalizedName = fullName.trim();
        String normalizedEmail = normalizeEmail(email);

        if (normalizedName.isEmpty() || normalizedName.length() > 120) {
            return response(HttpStatus.BAD_REQUEST, "Họ tên không được để trống và tối đa 120 ký tự.");
        }
        if (!isValidEmail(normalizedEmail)) {
            return response(HttpStatus.BAD_REQUEST, "Email chưa đúng định dạng.");
        }
        if (!isValidPassword(password)) {
            return response(HttpStatus.BAD_REQUEST, "Mật khẩu cần có ít nhất 8 ký tự và tối đa 72 byte UTF-8.");
        }
        if (!termsAccepted) {
            return response(HttpStatus.BAD_REQUEST, "Bạn cần đồng ý với điều khoản để tiếp tục.");
        }

        String passwordHash = passwordEncoder.encode(password);
        try {
            jdbcTemplate.update("""
                    INSERT INTO users (full_name, email, password_hash, role, status)
                    VALUES (?, ?, ?, 'STUDENT', 'ACTIVE')
                    """, normalizedName, normalizedEmail, passwordHash);
        } catch (DuplicateKeyException exception) {
            return response(HttpStatus.CONFLICT, "Email này đã được đăng ký.");
        }

        return response(HttpStatus.CREATED, "Đăng ký thành công. Bạn có thể đăng nhập.");
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> login(@RequestParam String email, @RequestParam String password) {
        String normalizedEmail = normalizeEmail(email);
        if (!isValidEmail(normalizedEmail) || password == null || password.isBlank()) {
            return response(HttpStatus.BAD_REQUEST, "Vui lòng nhập email hợp lệ và mật khẩu.");
        }

        List<LoginUser> users = jdbcTemplate.query("""
                SELECT id, full_name, password_hash, status, role
                FROM users
                WHERE email = ?
                """, (resultSet, rowNumber) -> new LoginUser(
                resultSet.getString("full_name"),
                resultSet.getString("password_hash"),
                resultSet.getString("status"),
                resultSet.getString("role"),
                resultSet.getLong("id")), normalizedEmail);

        if (users.isEmpty() || !passwordEncoder.matches(password, users.getFirst().passwordHash())) {
            return response(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác.");
        }
        if (!"ACTIVE".equals(users.getFirst().status())) {
            return response(HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa.");
        }

        LoginUser user = users.getFirst();
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(12);
        jdbcTemplate.update("INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                user.id(), sha256(token), expiresAt);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header("X-Auth-Token", token)
                .header("X-User-Role", user.role())
                .header("X-User-Name", user.fullName())
                .header("X-User-Email", normalizedEmail)
                .body(user.role());
    }

    @PostMapping(path = "/logout", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> logout(@org.springframework.web.bind.annotation.RequestHeader(
            name = "Authorization", required = false) String authorization) {
        String token = bearerToken(authorization);
        if (token != null) {
            jdbcTemplate.update("UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP(3) WHERE token_hash = ? AND revoked_at IS NULL",
                    sha256(token));
        }
        return response(HttpStatus.OK, "Đã đăng xuất.");
    }

    private static String bearerToken(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim() : null;
    }

    private static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> databaseError(DataAccessException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE,
                "Không truy cập được bảng users. Hãy kiểm tra kết nối database và chạy migration tạo bảng.");
    }

    private static boolean isValidEmail(String email) {
        return email.length() <= 254 && EMAIL_PATTERN.matcher(email).matches();
    }

    private static boolean isValidPassword(String password) {
        if (password == null) {
            return false;
        }
        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        return byteLength >= 8 && byteLength <= 72;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseEntity<String> response(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(body);
    }

    private record LoginUser(String fullName, String passwordHash, String status, String role, long id) {
    }
}
