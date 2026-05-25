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
        return null;
    }

    // =========================================================================
    // 4. LẤY ID CỦA USER
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
    // 5. ADMIN — LẤY DANH SÁCH TẤT CẢ NGƯỜI DÙNG (kèm số dư ví)
    //
    // Trả về List<String[]>, mỗi phần tử là:
    //   [id, username, email, role, created_at, balance]
    //
    // Nếu cột balance chưa tồn tại (chưa chạy migration), tự động
    // thêm cột và trả về 0.00 cho tất cả user, không crash app.
    // =========================================================================
    public List<String[]> getAllUsers() {
        List<String[]> users = new java.util.ArrayList<>();

        // Đảm bảo cột balance tồn tại — tự động tạo nếu chưa có
        ensureBalanceColumn();

        String sql = "SELECT id, username, email, role, created_at, "
                + "COALESCE(balance, 0.00) AS balance "
                + "FROM users ORDER BY id";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                users.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("username"),
                        rs.getString("email") != null ? rs.getString("email") : "",
                        rs.getString("role"),
                        rs.getString("created_at") != null ? rs.getString("created_at") : "",
                        String.format("%.2f", rs.getDouble("balance"))
                });
            }

        } catch (SQLException e) {
            System.err.println("[UserDAO] getAllUsers lỗi: " + e.getMessage());
        }
        return users;
    }

    // =========================================================================
    // HELPER — Tự động thêm cột balance nếu chưa tồn tại trong bảng users
    // Gọi khi admin lấy danh sách user, an toàn nếu cột đã tồn tại.
    // =========================================================================
    private void ensureBalanceColumn() {
        String checkSql = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'users' "
                + "AND COLUMN_NAME = 'balance'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement check = conn.prepareStatement(checkSql);
             ResultSet rs = check.executeQuery()) {

            if (rs.next() && rs.getInt(1) == 0) {
                // Cột chưa tồn tại → tạo mới
                String alterSql = "ALTER TABLE users "
                        + "ADD COLUMN balance DECIMAL(15,2) NOT NULL DEFAULT 0.00";
                try (PreparedStatement alter = conn.prepareStatement(alterSql)) {
                    alter.executeUpdate();
                    System.out.println("[UserDAO] Đã tự động thêm cột 'balance' vào bảng users.");
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] ensureBalanceColumn lỗi: " + e.getMessage());
        }
    }

    // =========================================================================
    // 6. ADMIN — XÓA NGƯỜI DÙNG
    // =========================================================================
    public boolean deleteUser(String usernameToDelete, String requestingAdmin) {
        if (usernameToDelete.equals(requestingAdmin)) {
            System.err.println("[UserDAO] Admin không thể tự xóa chính mình: " + requestingAdmin);
            return false;
        }

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
    // =========================================================================
    public boolean updateUserRole(String targetUsername, String newRole, String requestingAdmin) {
        if (!newRole.equals("BIDDER") && !newRole.equals("SELLER")) {
            System.err.println("[UserDAO] updateUserRole: role không hợp lệ '" + newRole + "'");
            return false;
        }
        if (targetUsername.equals(requestingAdmin)) {
            System.err.println("[UserDAO] Admin không thể đổi role chính mình.");
            return false;
        }
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
    // 8. VÍ TIỀN — LẤY SỐ DƯ
    // =========================================================================
    public double getBalance(String username) {
        ensureBalanceColumn();
        String sql = "SELECT COALESCE(balance, 0.00) AS balance FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] getBalance lỗi: " + e.getMessage());
        }
        return -1;
    }

    // =========================================================================
    // 9. VÍ TIỀN — NẠP TIỀN
    //
    // Chỉ cho phép nạp số dương.
    // Trả về số dư mới sau khi nạp, hoặc -1 nếu thất bại.
    // =========================================================================
    public double deposit(String username, double amount) {
        if (amount <= 0) {
            System.err.println("[UserDAO] deposit: số tiền phải > 0");
            return -1;
        }
        String sql = "UPDATE users SET balance = balance + ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, amount);
            pstmt.setString(2, username);
            if (pstmt.executeUpdate() > 0) {
                double newBalance = getBalance(username);
                System.out.println("[UserDAO] '" + username + "' nạp $"
                        + amount + " → số dư mới: $" + newBalance);
                return newBalance;
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] deposit lỗi: " + e.getMessage());
        }
        return -1;
    }

    // =========================================================================
    // HELPER — Hash mật khẩu bằng SHA-256
    // =========================================================================
    static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 không khả dụng", e);
        }
    }
}