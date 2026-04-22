package com.auction.server.dao;

// Các dòng import "đồ nghề" để viết Test
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UserDAOTest {

    private UserDAO userDAO;

    @BeforeEach
    public void setUp() {
        // Khởi tạo DAO trước mỗi bài test
        userDAO = new UserDAO();
    }

    @Test
    public void testRegisterAndAuthenticateUser() {
        // 1. Dữ liệu giả lập (thêm timestamp để không bị trùng username khi chạy test nhiều lần)
        String testUsername = "testuser_" + System.currentTimeMillis();
        String testEmail = testUsername + "@gmail.com";
        String testPassword = "password123";

        // 2. Kiểm thử tính năng Đăng ký
        boolean isRegistered = userDAO.registerUser(testUsername, testPassword, testEmail, "BIDDER");
        assertTrue(isRegistered, "Đăng ký tài khoản phải trả về true");

        // 3. Kiểm thử tính năng Đăng nhập với mật khẩu ĐÚNG
        boolean isLoginSuccess = userDAO.authenticateUser(testUsername, testPassword);
        assertTrue(isLoginSuccess, "Đăng nhập với mật khẩu đúng phải trả về true");

        // 4. Kiểm thử tính năng Đăng nhập với mật khẩu SAI
        boolean isLoginFail = userDAO.authenticateUser(testUsername, "wrong_password");
        assertFalse(isLoginFail, "Đăng nhập với mật khẩu sai phải trả về false");
    }
}