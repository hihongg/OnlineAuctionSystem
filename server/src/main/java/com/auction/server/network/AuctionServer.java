package com.auction.server.network;

import com.auction.shared.models.Message;
import com.auction.server.services.AuctionService;
import com.auction.server.utils.DatabaseConnection;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AuctionServer {

    private final int port;
    private final List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(50);
    private final AuctionService auctionService;

    // FIX: đưa serverSocket thành field để shutdown() có thể đóng nó.
    // Trước đây serverSocket nằm trong try-with-resources của start() nên
    // shutdown() không thể chạm tới — server.accept() sẽ block mãi mãi
    // dù pool đã shutdown.
    private ServerSocket serverSocket;

    public AuctionServer(int port) {
        this.port = port;
        this.auctionService = new AuctionService(this);
    }

    public void start() {
        System.out.println("[SERVER] Máy chủ đấu giá đang khởi động trên port " + port + "...");

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[SERVER] Đang chờ Client kết nối...");

            // Dùng !serverSocket.isClosed() thay vì while(true) để vòng lặp
            // thoát ngay khi shutdown() đóng serverSocket
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] Client mới kết nối: " + clientSocket.getInetAddress());

                ClientHandler clientThread = new ClientHandler(clientSocket, this, auctionService);
                connectedClients.add(clientThread);
                pool.execute(clientThread);
            }
        } catch (IOException e) {
            // Bỏ qua exception khi serverSocket bị đóng chủ động qua shutdown()
            if (serverSocket != null && !serverSocket.isClosed()) {
                System.err.println("[SERVER] Lỗi khởi động server: " + e.getMessage());
            } else {
                System.out.println("[SERVER] ServerSocket đã đóng — vòng accept() kết thúc.");
            }
        }
    }

    /**
     * Gửi tin nhắn đến TẤT CẢ client đang kết nối.
     * Dùng cho các thông báo hệ thống không liên quan đến item cụ thể
     * (ví dụ: server shutdown, thông báo toàn hệ thống).
     */
    public void broadcast(Message message) {
        String jsonMessage = message.toJson();
        for (ClientHandler client : connectedClients) {
            client.sendMessage(jsonMessage);
        }
    }

    /**
     * Gửi tin nhắn CHỈ đến các client đang theo dõi (watch) một item cụ thể.
     *
     * Tại sao cần method này thay vì dùng broadcast()?
     *   - broadcast() gửi BID_UPDATE cho mọi client dù họ không xem item đó
     *     → lãng phí băng thông, client phải tự lọc.
     *   - broadcastToItemWatchers() chỉ gửi đến đúng người cần nhận
     *     → hiệu quả hơn khi có nhiều phiên đấu giá đồng thời.
     *
     * Client đăng ký xem bằng lệnh WATCH_ITEM:<itemId>
     * Client hủy xem bằng lệnh UNWATCH_ITEM hoặc khi ngắt kết nối.
     *
     * @param itemId  ID của item cần broadcast
     * @param message Tin nhắn cần gửi (BID_UPDATE, TIME_EXTENDED, ...)
     */
    public void broadcastToItemWatchers(int itemId, Message message) {
        String jsonMessage = message.toJson();
        int count = 0;
        for (ClientHandler client : connectedClients) {
            if (client.getWatchedItemId() == itemId) {
                client.sendMessage(jsonMessage);
                count++;
            }
        }
        System.out.printf("[SERVER] Broadcast item #%d → %d watcher(s): %s%n",
                itemId, count, message.getAction());
    }

    public void removeClient(ClientHandler client) {
        connectedClients.remove(client);
        System.out.println("[SERVER] Một Client đã ngắt kết nối. Tổng số online: " + connectedClients.size());
    }

    /**
     * Tắt server theo đúng thứ tự để tránh mất dữ liệu và rò rỉ tài nguyên.
     *
     * Thứ tự shutdown quan trọng:
     *   1. Đóng ServerSocket  → ngăn client mới kết nối vào
     *   2. Tắt AuctionService → dừng scheduler kiểm tra phiên đấu giá
     *   3. Tắt thread pool    → các ClientHandler đang chạy được phép hoàn thành
     *   4. Chờ pool kết thúc  → đảm bảo mọi giao dịch DB đang thực hiện xong xuôi
     *   5. Đóng DB pool       → giải phóng kết nối HikariCP một cách sạch sẽ
     *
     * Tại sao phải gọi awaitTermination() trước khi đóng DB?
     *   - Nếu đóng DB pool ngay sau pool.shutdown(), các thread ClientHandler
     *     vẫn đang chạy có thể đang giữa chừng một transaction SQL.
     *   - Lấy connection từ pool đã đóng sẽ ném SQLException → dữ liệu bị mất.
     *   - awaitTermination(10s) chờ tối đa 10 giây để các thread tự kết thúc,
     *     sau đó mới tiến hành đóng DB — đảm bảo không có transaction nào bị cắt ngang.
     */
    public void shutdown() {
        System.out.println("[SERVER] Đang tắt server...");

        // Bước 1: Đóng ServerSocket → accept() ném SocketException → vòng lặp trong start() thoát
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Lỗi khi đóng ServerSocket: " + e.getMessage());
        }

        // Bước 2: Dừng AuctionService (scheduler kiểm tra phiên + xử lý autoBids)
        auctionService.shutdown();

        // Bước 3: Yêu cầu thread pool dừng nhận task mới
        // Các ClientHandler đang chạy vẫn được phép hoàn thành công việc hiện tại
        pool.shutdown();

        // Bước 4: Chờ tối đa 10 giây để các ClientHandler thread kết thúc
        // Quan trọng: phải chờ TRƯỚC khi đóng DB pool,
        // vì các thread có thể đang thực hiện transaction SQL dở dang.
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                // Hết 10 giây mà vẫn còn thread → ép dừng ngay
                pool.shutdownNow();
                System.out.println("[SERVER] Ép dừng các thread còn lại sau 10 giây chờ.");
            }
        } catch (InterruptedException e) {
            // Thread hiện tại bị interrupt trong khi đang chờ → ép dừng pool
            pool.shutdownNow();
            Thread.currentThread().interrupt(); // khôi phục trạng thái interrupt
        }

        // Bước 5: Đóng connection pool HikariCP — an toàn vì tất cả thread đã xong
        // Nếu không gọi bước này: HikariCP giữ các TCP connection tới MySQL
        // không giải phóng → MySQL báo "Too many connections" ở lần khởi động sau.
        DatabaseConnection.shutdown();

        System.out.println("[SERVER] Server đã tắt hoàn toàn.");
    }
}