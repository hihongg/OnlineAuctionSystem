package com.auction.server.dao;

import com.auction.server.utils.DatabaseConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.*;

public class UserDAOTest {

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

    @Test
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
}