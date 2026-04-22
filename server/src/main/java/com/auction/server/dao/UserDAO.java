package com.auction.server.dao;

import com.auction.server.utils.DatabaseConnection;
import com.auction.shared.models.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UserDAO {
    private Connection connection;

    public UserDAO() {
        // Lấy kết nối từ Singleton
        this.connection = DatabaseConnection.getInstance().getConnection();
    }

    // Phương thức thêm người dùng mới vào Database
    public boolean addUser(User user) {
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            // Gán các tham số vào câu lệnh SQL
            preparedStatement.setString(1, user.getUsername());
            preparedStatement.setString(2, user.getPassword());
            preparedStatement.setString(3, user.getRole());

            // Thực thi câu lệnh
            int rowsAffected = preparedStatement.executeUpdate();
            return rowsAffected > 0; // Trả về true nếu thêm thành công

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}