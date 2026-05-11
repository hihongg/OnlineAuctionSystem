package com.auction.server.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Lớp quản lý kết nối Cơ sở dữ liệu sử dụng mẫu thiết kế Singleton.
 * Đảm bảo chỉ có một kết nối duy nhất được tạo ra trong suốt vòng đời của Server.
 */
public class DatabaseConnection {
    // Biến instance duy nhất
    private static DatabaseConnection instance;
    private static Connection connection;

    // Cấu hình Database (Mặc định dùng MySQL)
    // "auction_db" là tên cơ sở dữ liệu chúng ta sẽ tạo sau
    private static final String URL = "jdbc:mysql://localhost:3306/auction_db";
    private static final String USER = "root"; // Thay bằng username DB của bạn
    private static final String PASSWORD = "123456"; // Thay bằng mật khẩu DB của bạn

    // Constructor private để ngăn việc khởi tạo từ bên ngoài
    private DatabaseConnection() {
        try {
            // Tải driver MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");
            // Thực hiện kết nối
            this.connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Kết nối cơ sở dữ liệu thành công!");
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println("Lỗi kết nối cơ sở dữ liệu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Phương thức public duy nhất để lấy instance
    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    // Phương thức lấy đối tượng Connection để các lớp DAO sử dụng
    public static Connection getConnection() {
        return connection;
    }
}