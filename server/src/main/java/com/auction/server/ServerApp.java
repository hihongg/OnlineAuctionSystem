package com.auction.server;

import com.auction.server.network.AuctionServer;
import com.auction.server.utils.DatabaseConnection; // Điều chỉnh import theo đúng package của bạn

public class ServerApp {
    public static void main(String[] args) {
        System.out.println("=== HỆ THỐNG ĐẤU GIÁ TRỰC TUYẾN - SERVER KHỞI ĐỘNG ===");

        // 1. Kiểm tra kết nối Cơ sở dữ liệu (Database)
        try {
            if (DatabaseConnection.getConnection() != null) {
                System.out.println("[DB] Kết nối cơ sở dữ liệu MySQL thành công!");
            }
        } catch (Exception e) {
            System.err.println("[DB] Lỗi kết nối CSDL: " + e.getMessage());
            System.err.println("[SERVER] Máy chủ không thể hoạt động nếu thiếu DB. Đang tắt hệ thống...");
            return; // Dừng chương trình nếu không có DB
        }

        // 2. Thiết lập cổng (Port) cho Server
        int port = 8080; // Bạn có thể thống nhất cổng này với các bạn làm Client

        // 3. Khởi tạo và chạy mạng (Network Socket)
        try {
            AuctionServer server = new AuctionServer(port);
            System.out.println("[SERVER] Máy chủ đã sẵn sàng. Đang lắng nghe kết nối tại cổng: " + port);

            // Hàm start() sẽ có vòng lặp while(true) để liên tục nhận Client
            server.start();
        } catch (Exception e) {
            System.err.println("[SERVER] Lỗi khi chạy máy chủ: " + e.getMessage());
        }
    }
}