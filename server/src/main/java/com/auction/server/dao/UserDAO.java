package com.auction.server.dao;

import com.auction.server.utils.DatabaseConnection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * DAO thao tác với bảng users.
 *
 * Mỗi phương thức tự lấy Connection từ pool và trả lại ngay khi xong
 * (try-with-resources). Không lưu Connection vào field — đây là lý do
 * phiên bản cũ không thread-safe: mọi thread dùng chung 1 connection.
 */
public class UserDAO {

    // =========================================================================
    // 1. ĐĂNG KÝ
    // =========================================================================
    public boolean registerUser(String username, String password,
                                String email, String role) {
        String sql = "INSERT INTO users (username, password, email, role) "
                + "VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, hashPassword(password));
            pstmt.setString(3, email);
            pstmt.setString(4, role);

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            // username đã tồn tại (UNIQUE constraint) → không cần stacktrace
            System.err.println("[UserDAO] registerUser thất bại: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // 2. ĐĂNG NHẬP
    // =========================================================================
    public boolean authenticateUser(String username, String password) {
        String sql = "SELECT 1 FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, hashPassword(password));

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            System.err.println("[UserDAO] authenticateUser lỗi: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // 3. LẤY THÔNG TIN USER (username + role)
    // =========================================================================
    public String[] getUserInfo(String username) {
        String sql = "SELECT username, role FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new String[]{
                            rs.getString("username"),
                            rs.getString("role")
                    };
                }
            }

        } catch (SQLException e) {
            System.err.println("[UserDAO] getUserInfo lỗi: " + e.getMessage());
        }
        return null;  // ← BUG CŨ: thiếu dòng này, khiến các phương thức bên dưới
        //   bị hiểu là nằm bên trong getUserInfo → lỗi compile
    }

    // =========================================================================
    // 4. LẤY ID CỦA USER (dùng khi Seller thêm sản phẩm — cần seller_id)
    // =========================================================================
    public int getUserIdByUsername(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] getUserIdByUsername lỗi: " + e.getMessage());
        }
        return -1;
    }

    // =========================================================================
    // 5. ADMIN — LẤY DANH SÁCH TẤT CẢ NGƯỜI DÙNG
    //
    // Trả về List<String[]>, mỗi phần tử là [id, username, email, role, created_at].
    // KHÔNG trả password (dù đã hash) — tối thiểu hóa dữ liệu nhạy cảm.
    // =========================================================================
    public List<String[]> getAllUsers() {
        String sql = "SELECT id, username, email, role, created_at FROM users ORDER BY id";
        List<String[]> users = new java.util.ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                users.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("username"),
                        rs.getString("email") != null ? rs.getString("email") : "",
                        rs.getString("role"),
                        rs.getString("created_at")
                });
            }

        } catch (SQLException e) {
            System.err.println("[UserDAO] getAllUsers lỗi: " + e.getMessage());
        }
        return users;
    }

    // =========================================================================
    // 6. ADMIN — XÓA NGƯỜI DÙNG
    //
    // Chỉ xóa được BIDDER và SELLER.
    // ADMIN không được tự xóa chính mình và không xóa được admin khác
    // → tránh mất toàn bộ quyền quản trị.
    //
    // Lưu ý: do FK seller_id REFERENCES users(id) ON DELETE SET NULL,
    //   các sản phẩm của Seller bị xóa sẽ có seller_id = NULL (không mất dữ liệu đấu giá).
    // =========================================================================
    public boolean deleteUser(String usernameToDelete, String requestingAdmin) {
        // Không cho phép admin tự xóa chính mình
        if (usernameToDelete.equals(requestingAdmin)) {
            System.err.println("[UserDAO] Admin không thể tự xóa chính mình: " + requestingAdmin);
            return false;
        }

        // Không cho phép xóa tài khoản có role ADMIN
        String[] targetInfo = getUserInfo(usernameToDelete);
        if (targetInfo == null) {
            System.err.println("[UserDAO] deleteUser: không tìm thấy user '" + usernameToDelete + "'");
            return false;
        }
        if ("ADMIN".equals(targetInfo[1])) {
            System.err.println("[UserDAO] Không được xóa tài khoản ADMIN: " + usernameToDelete);
            return false;
        }

        String sql = "DELETE FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usernameToDelete);
            boolean deleted = pstmt.executeUpdate() > 0;
            if (deleted) {
                System.out.println("[UserDAO] Admin '" + requestingAdmin
                        + "' đã xóa user '" + usernameToDelete + "'");
            }
            return deleted;

        } catch (SQLException e) {
            System.err.println("[UserDAO] deleteUser lỗi: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // 7. ADMIN — ĐỔI ROLE NGƯỜI DÙNG
    //
    // Cho phép Admin nâng/hạ quyền: BIDDER ↔ SELLER.
    // KHÔNG cho phép đổi thành ADMIN (tránh leo thang đặc quyền).
    // KHÔNG cho phép đổi role của chính admin đang thực hiện.
    // =========================================================================
    public boolean updateUserRole(String targetUsername, String newRole, String requestingAdmin) {
        // Validate role mới
        if (!newRole.equals("BIDDER") && !newRole.equals("SELLER")) {
            System.err.println("[UserDAO] updateUserRole: role không hợp lệ '" + newRole + "'");
            return false;
        }
        // Không cho phép đổi role chính mình
        if (targetUsername.equals(requestingAdmin)) {
            System.err.println("[UserDAO] Admin không thể đổi role chính mình.");
            return false;
        }
        // Không cho phép đổi role của ADMIN khác
        String[] targetInfo = getUserInfo(targetUsername);
        if (targetInfo != null && "ADMIN".equals(targetInfo[1])) {
            System.err.println("[UserDAO] Không được đổi role của tài khoản ADMIN.");
            return false;
        }

        String sql = "UPDATE users SET role = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newRole);
            pstmt.setString(2, targetUsername);
            boolean updated = pstmt.executeUpdate() > 0;
            if (updated) {
                System.out.println("[UserDAO] Admin '" + requestingAdmin
                        + "' đổi role của '" + targetUsername + "' → " + newRole);
            }
            return updated;

        } catch (SQLException e) {
            System.err.println("[UserDAO] updateUserRole lỗi: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // HELPER — Hash mật khẩu bằng SHA-256 trước khi lưu / so sánh DB
    //
    // Tại sao không lưu plain text?
    //   - Nếu DB bị lộ, toàn bộ mật khẩu người dùng bị lộ ngay lập tức.
    //   - SHA-256 là one-way hash: không thể đảo ngược → an toàn hơn.
    //
    // Lưu ý nâng cao: production nên dùng bcrypt/Argon2 (có salt tự động).
    //   SHA-256 ở đây đủ cho mục đích học tập / bài tập lớn.
    // =========================================================================
    static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 luôn tồn tại trong JDK chuẩn — không bao giờ xảy ra
            throw new RuntimeException("SHA-256 không khả dụng", e);
        }
    }
}