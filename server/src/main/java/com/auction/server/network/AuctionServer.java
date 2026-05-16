package com.auction.server.network;

import com.auction.shared.models.Message;
import com.auction.server.services.AuctionService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuctionServer {

    private int port;
    private List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
    private ExecutorService pool = Executors.newFixedThreadPool(50);
    private AuctionService auctionService;

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

    public void shutdown() {
        // 1. Đóng serverSocket → accept() ném SocketException → vòng lặp trong start() thoát
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Lỗi khi đóng ServerSocket: " + e.getMessage());
        }
        // 2. Dừng AuctionService (scheduler + autoBids)
        auctionService.shutdown();
        // 3. Chờ các ClientHandler thread hoàn thành
        pool.shutdown();
    }
}