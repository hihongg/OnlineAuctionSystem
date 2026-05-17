package com.auction.server.dao;

import com.auction.server.utils.DatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserDAO — Kiểm thử các thao tác người dùng")
public class UserDAOTest {

    private static final String ADMIN_USERNAME = "admin"; // user admin mặc định từ auction_db.sql

    private UserDAO userDAO;
    // TEST HYGIENE FIX: lưu lại username để @AfterEach có thể dọn dẹp.
    private String testUsername;

    @BeforeEach
    public void setUp() {
        userDAO = new UserDAO();
        // Dùng timestamp để không bị trùng username khi chạy test nhiều lần
        testUsername = "testuser_" + System.currentTimeMillis();
    }

    // TEST HYGIENE FIX: Thêm @AfterEach để xóa user test sau mỗi test case.
    // Trước đây không có bước dọn dẹp → DB tích lũy hàng loạt user rác
    // mỗi lần chạy test, có thể làm chậm các truy vấn hoặc gây nhiễu.
    @AfterEach
    public void tearDown() throws Exception {
        if (testUsername == null) return;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM users WHERE username = ?")) {
            pstmt.setString(1, testUsername);
            pstmt.executeUpdate();
        }
    }

    // =========================================================================
    // TEST 1: Đăng ký & Đăng nhập cơ bản
    // =========================================================================
    @Test
    @DisplayName("Đăng ký thành công → đăng nhập đúng pass → đăng nhập sai pass")
    public void testRegisterAndAuthenticateUser() {
        String testEmail    = testUsername + "@gmail.com";
        String testPassword = "password123";

        // Kiểm thử tính năng Đăng ký
        boolean isRegistered = userDAO.registerUser(testUsername, testPassword, testEmail, "BIDDER");
        assertTrue(isRegistered, "Đăng ký tài khoản phải trả về true");

        // Kiểm thử tính năng Đăng nhập với mật khẩu ĐÚNG
        boolean isLoginSuccess = userDAO.authenticateUser(testUsername, testPassword);
        assertTrue(isLoginSuccess, "Đăng nhập với mật khẩu đúng phải trả về true");

        // Kiểm thử tính năng Đăng nhập với mật khẩu SAI
        boolean isLoginFail = userDAO.authenticateUser(testUsername, "wrong_password");
        assertFalse(isLoginFail, "Đăng nhập với mật khẩu sai phải trả về false");
    }

    // =========================================================================
    // TEST 2: Không được phép đăng ký trùng username
    //
    // Bảng users có UNIQUE constraint trên cột username.
    // Đăng ký lần 2 cùng username phải trả về false (không throw exception).
    // =========================================================================
    @Test
    @DisplayName("Đăng ký trùng username → phải trả về false")
    public void testRegisterDuplicateUsername_shouldFail() {
        userDAO.registerUser(testUsername, "pass1", testUsername + "@a.com", "BIDDER");

        boolean secondRegister = userDAO.registerUser(testUsername, "pass2", testUsername + "@b.com", "SELLER");

        assertFalse(secondRegister,
                "Đăng ký username đã tồn tại phải trả về false (vi phạm UNIQUE constraint)");
    }

    // =========================================================================
    // TEST 3: getUserInfo() trả về đúng username và role
    //
    // Sau khi đăng ký, getUserInfo() phải trả về mảng [username, role] đúng.
    // Trường hợp user không tồn tại phải trả về null (không throw exception).
    // =========================================================================
    @Test
    @DisplayName("getUserInfo() trả về đúng dữ liệu hoặc null nếu không tồn tại")
    public void testGetUserInfo_returnsCorrectData() {
        userDAO.registerUser(testUsername, "pass", testUsername + "@c.com", "SELLER");

        String[] info = userDAO.getUserInfo(testUsername);

        assertNotNull(info, "getUserInfo() phải trả về mảng, không phải null");
        assertEquals(2, info.length, "Mảng phải có đúng 2 phần tử: [username, role]");
        assertEquals(testUsername, info[0], "info[0] phải là username");
        assertEquals("SELLER",    info[1], "info[1] phải là role vừa đăng ký");

        // Trường hợp user không tồn tại
        String[] notFound = userDAO.getUserInfo("__user_khong_ton_tai__");
        assertNull(notFound, "getUserInfo() phải trả về null khi username không tồn tại");
    }

    // =========================================================================
    // TEST 4: getUserIdByUsername() trả về ID hợp lệ
    //
    // ID phải > 0 (AUTO_INCREMENT từ 1) sau khi đăng ký thành công.
    // ID phải là -1 khi user không tồn tại.
    // =========================================================================
    @Test
    @DisplayName("getUserIdByUsername() trả về ID > 0 hoặc -1 nếu không tìm thấy")
    public void testGetUserIdByUsername_returnsValidId() {
        userDAO.registerUser(testUsername, "pass", testUsername + "@d.com", "BIDDER");

        int id = userDAO.getUserIdByUsername(testUsername);
        assertTrue(id > 0, "ID phải lớn hơn 0 với user hợp lệ");

        int notFoundId = userDAO.getUserIdByUsername("__user_khong_ton_tai__");
        assertEquals(-1, notFoundId, "ID phải là -1 khi username không tồn tại");
    }

    // =========================================================================
    // TEST 5: Admin không được tự xóa chính mình và không xóa được ADMIN khác
    //
    // Quy tắc nghiệp vụ: tránh mất toàn bộ quyền quản trị hệ thống.
    // =========================================================================
    @Test
    @DisplayName("deleteUser() — Admin không thể tự xóa chính mình")
    public void testDeleteUser_adminCannotDeleteSelf() {
        boolean result = userDAO.deleteUser(ADMIN_USERNAME, ADMIN_USERNAME);

        assertFalse(result, "Admin không được phép xóa chính mình");
    }

    @Test
    @DisplayName("deleteUser() — Xóa BIDDER thành công, sau đó không tìm thấy user")
    public void testDeleteUser_shouldSucceedForBidder() {
        userDAO.registerUser(testUsername, "pass", testUsername + "@e.com", "BIDDER");

        boolean deleted = userDAO.deleteUser(testUsername, ADMIN_USERNAME);
        assertTrue(deleted, "Admin phải xóa được BIDDER thành công");

        // Xác nhận user thực sự đã bị xóa khỏi DB
        String[] info = userDAO.getUserInfo(testUsername);
        assertNull(info, "User đã bị xóa không được tìm thấy nữa");
    }

    // =========================================================================
    // TEST 6: updateUserRole() — đổi role hợp lệ và chặn đổi thành ADMIN
    //
    // Chỉ được đổi giữa BIDDER ↔ SELLER.
    // Không được đổi thành ADMIN (leo thang đặc quyền).
    // =========================================================================
    @Test
    @DisplayName("updateUserRole() — đổi BIDDER → SELLER thành công")
    public void testUpdateUserRole_bidderToSeller_shouldSucceed() {
        userDAO.registerUser(testUsername, "pass", testUsername + "@f.com", "BIDDER");

        boolean updated = userDAO.updateUserRole(testUsername, "SELLER", ADMIN_USERNAME);
        assertTrue(updated, "Đổi role BIDDER → SELLER phải thành công");

        String[] info = userDAO.getUserInfo(testUsername);
        assertNotNull(info);
        assertEquals("SELLER", info[1], "Role phải được cập nhật thành SELLER trong DB");
    }

    @Test
    @DisplayName("updateUserRole() — đổi thành ADMIN phải bị từ chối")
    public void testUpdateUserRole_toAdmin_shouldFail() {
        userDAO.registerUser(testUsername, "pass", testUsername + "@g.com", "BIDDER");

        boolean updated = userDAO.updateUserRole(testUsername, "ADMIN", ADMIN_USERNAME);
        assertFalse(updated, "Không được phép đổi role lên ADMIN (leo thang đặc quyền)");

        // Xác nhận role trong DB không thay đổi
        String[] info = userDAO.getUserInfo(testUsername);
        assertNotNull(info);
        assertEquals("BIDDER", info[1], "Role phải vẫn là BIDDER sau khi bị từ chối");
    }
}