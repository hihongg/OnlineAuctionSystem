package com.auction.server.dao.dao;

import com.auction.server.dao.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {
    private Connection connection;

    public UserDAO() {
        // Lấy kết nối từ Singleton
        this.connection = DatabaseConnection.getInstance().getConnection();
    }

    // =========================================================================
    // 1. HÀM ĐĂNG KÝ (Khớp với lệnh registerUser trong file Test)
    // =========================================================================
    public boolean registerUser(String username, String password, String email, String role) {
        // SQL Thêm người dùng (Lưu ý: Đảm bảo bảng 'users' trong Database của bạn có cột 'email')
        String sql = "INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, ?)";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            // Gán 4 tham số từ file test vào SQL
            preparedStatement.setString(1, username);
            preparedStatement.setString(2, password);
            preparedStatement.setString(3, email);
            preparedStatement.setString(4, role);

            // Thực thi câu lệnh
            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0; // Trả về true nếu thêm thành công

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // 2. HÀM ĐĂNG NHẬP (Khớp với lệnh authenticateUser trong file Test)
    // =========================================================================
    public boolean authenticateUser(String username, String password) {
        // SQL Kiểm tra xem có tài khoản nào khớp cả username lẫn password không
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            preparedStatement.setString(2, password);

            // Thực thi truy vấn lấy dữ liệu
            ResultSet resultSet = preparedStatement.executeQuery();

            // Nếu resultSet.next() là true => Tìm thấy tài khoản hợp lệ trong DB => Đăng nhập thành công
            return resultSet.next();

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}