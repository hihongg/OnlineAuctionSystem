package com.auction.server;

import com.auction.server.network.AuctionServer;
import com.auction.server.utils.DatabaseConnection;

public class ServerApp {

    // Cổng phải khớp với DEFAULT_PORT trong ClientSocketManager.java (client)
    private static final int PORT = 12345;

    public static void main(String[] args) {
        System.out.println("=== HỆ THỐNG ĐẤU GIÁ TRỰC TUYẾN - SERVER KHỞI ĐỘNG ===");

        // 1. Kiểm tra kết nối Cơ sở dữ liệu (Database)
        // FIX: dùng try-with-resources để connection được trả về pool ngay sau kiểm tra.
        // Phiên bản cũ: getConnection() != null — connection lấy ra nhưng không bao giờ
        // được đóng → rò rỉ 1 connection khỏi HikariCP pool khi server khởi động.
        try (java.sql.Connection testConn = DatabaseConnection.getConnection()) {
            System.out.println("[DB] Kết nối cơ sở dữ liệu MySQL thành công!");
        } catch (Exception e) {
            System.err.println("[DB] Lỗi kết nối CSDL: " + e.getMessage());
            System.err.println("[SERVER] Máy chủ không thể hoạt động nếu thiếu DB. Đang tắt hệ thống...");
            return;
        }

        // 2. Khởi tạo Server
        AuctionServer server = new AuctionServer(PORT);

        // 3. Shutdown hook — đảm bảo giải phóng tài nguyên khi tắt server
        //    (Ctrl+C, kill, hoặc System.exit() đều kích hoạt hook này)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SERVER] Đang tắt hệ thống...");
            server.shutdown();          // dừng scheduler + thread pool
            DatabaseConnection.shutdown(); // đóng HikariCP connection pool
            System.out.println("[SERVER] Đã tắt an toàn.");
        }, "ShutdownHook"));

        // 4. Bắt đầu lắng nghe kết nối (vòng lặp vô hạn cho đến khi process bị kill)
        System.out.println("[SERVER] Máy chủ đã sẵn sàng. Đang lắng nghe tại cổng: " + PORT);
        server.start();
    }
}