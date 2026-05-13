package com.auction.server.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Quản lý kết nối CSDL sử dụng Connection Pool (HikariCP).
 *
 * Tại sao phải đổi từ Singleton Connection sang Pool?
 *   - Server có nhiều ClientHandler chạy đồng thời (mỗi client = 1 thread).
 *   - Một Connection dùng chung sẽ bị lỗi khi 2 thread cùng gọi
 *     setAutoCommit() hoặc rollback() → transaction của nhau bị phá.
 *   - Pool cấp cho mỗi thread một Connection riêng biệt,
 *     dùng xong tự trả về pool (không tạo mới mỗi lần → hiệu quả).
 *
 * Cách dùng trong DAO:
 *   try (Connection conn = DatabaseConnection.getConnection()) {
 *       // dùng conn...
 *   }  // ← tự động trả connection về pool khi ra khỏi try
 */
public class DatabaseConnection {

    // -------------------------------------------------------------------------
    // Cấu hình — chỉnh sửa 3 hằng này cho khớp với môi trường của bạn
    // -------------------------------------------------------------------------
    private static final String URL      = "jdbc:mysql://localhost:3306/auction_db"
            + "?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh"
            + "&allowPublicKeyRetrieval=true";
    private static final String DB_USER  = "root";
    private static final String DB_PASS  = "123456";

    // -------------------------------------------------------------------------
    // Pool (khởi tạo một lần khi class được load)
    // -------------------------------------------------------------------------
    private static final HikariDataSource DATA_SOURCE;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASS);

        // Số connection tối đa trong pool
        // = số ClientHandler chạy song song tối đa (hiện tại pool thread là 50)
        config.setMaximumPoolSize(50);

        // Số connection luôn "warm" sẵn sàng (giảm độ trễ khi burst)
        config.setMinimumIdle(5);

        // Thời gian chờ lấy connection từ pool trước khi throw exception (ms)
        config.setConnectionTimeout(30_000);

        // Kiểm tra connection còn sống trước khi cấp ra
        config.setConnectionTestQuery("SELECT 1");

        config.setPoolName("AuctionPool");

        DATA_SOURCE = new HikariDataSource(config);
        System.out.println("[DB] Connection pool đã khởi tạo thành công.");
    }

    // Constructor private — không cho phép tạo instance
    private DatabaseConnection() {}

    /**
     * Lấy một Connection từ pool.
     * Luôn dùng trong try-with-resources để tự động trả về pool:
     *
     *   try (Connection conn = DatabaseConnection.getConnection()) {
     *       ...
     *   }
     */
    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    /**
     * Gọi khi server tắt để giải phóng tài nguyên pool.
     * Thêm lệnh gọi này vào ServerApp hoặc AuctionServer.shutdown().
     */
    public static void shutdown() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
            System.out.println("[DB] Connection pool đã đóng.");
        }
    }
}