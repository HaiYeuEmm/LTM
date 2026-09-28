package vn.onlineexam.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TeacherAuthInterceptor implements HandlerInterceptor {
    public static final String USER_ID_ATTRIBUTE = "teacherUserId";
    private final JdbcTemplate jdbcTemplate;

    public TeacherAuthInterceptor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập lại.");
        }
        String tokenHash = hash(authorization.substring(7).trim());
        var users = jdbcTemplate.query("""
                SELECT u.id, u.role
                FROM auth_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.token_hash = ? AND s.revoked_at IS NULL
                  AND s.expires_at > CURRENT_TIMESTAMP(3) AND u.status = 'ACTIVE'
                """, (rs, row) -> new UserRole(rs.getLong("id"), rs.getString("role")), tokenHash);
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn.");
        }
        if (!"TEACHER".equals(users.getFirst().role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ giáo viên mới được dùng chức năng này.");
        }
        request.setAttribute(USER_ID_ATTRIBUTE, users.getFirst().id());
        return true;
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record UserRole(long id, String role) { }
}
