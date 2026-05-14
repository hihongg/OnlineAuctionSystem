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
 *
 * ──────────────────────────────────────────────────────────────────────────
 * CẤU HÌNH QUA BIẾN MÔI TRƯỜNG (ưu tiên hơn giá trị mặc định):
 *
 *   Biến môi trường  │ Mặc định (dev local)
 *   ─────────────────┼──────────────────────────────────────────────────────
 *   DB_URL           │ jdbc:mysql://localhost:3306/auction_db?...
 *   DB_USER          │ root
 *   DB_PASS          │ 123456
 *
 * Trên CI (GitHub Actions), workflow tự set các biến này trỏ tới
 * MySQL service container — không cần sửa code khi deploy.
 *
 * Trên máy local, không cần set gì — dùng giá trị mặc định bên dưới.
 * ──────────────────────────────────────────────────────────────────────────
 */
public class DatabaseConnection {

    // -------------------------------------------------------------------------
    // Đọc cấu hình theo thứ tự ưu tiên:
    //   1. Biến môi trường (CI / production)
    //   2. Giá trị mặc định hardcode (dev local)
    //
    // Tại sao dùng env var thay vì hardcode?
    //   - Không lộ mật khẩu thật trong mã nguồn commit lên GitHub.
    //   - CI/CD có thể ghi đè mà không cần sửa code.
    //   - Dễ deploy lên nhiều môi trường (dev / staging / production).
    // -------------------------------------------------------------------------
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/auction_db"
                    + "?useSSL=false"
                    + "&serverTimezone=Asia/Ho_Chi_Minh"
                    + "&allowPublicKeyRetrieval=true";

    private static final String URL     = getEnv("DB_URL",  DEFAULT_URL);
    private static final String DB_USER = getEnv("DB_USER", "root");
    private static final String DB_PASS = getEnv("DB_PASS", "123456");

    // -------------------------------------------------------------------------
    // Pool (khởi tạo một lần khi class được load lần đầu)
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
        System.out.println("[DB] Connection pool đã khởi tạo: " + maskUrl(URL));
    }

    // Constructor private — không cho phép tạo instance (Singleton pattern)
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

    // -------------------------------------------------------------------------
    // HELPER: đọc biến môi trường, dùng giá trị mặc định nếu không có
    // -------------------------------------------------------------------------
    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            System.out.println("[DB] Dùng biến môi trường: " + key);
            return value;
        }
        return defaultValue;
    }

    // -------------------------------------------------------------------------
    // HELPER: ẩn password trong URL khi in ra log (bảo mật)
    // -------------------------------------------------------------------------
    private static String maskUrl(String url) {
        // Ẩn phần sau "password=" nếu có trong URL
        return url.replaceAll("password=[^&]+", "password=***");
    }
}