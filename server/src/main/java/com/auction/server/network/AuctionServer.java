package com.auction.server.network;

import com.auction.shared.models.Message;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuctionServer {
    private int port;
    // Sử dụng CopyOnWriteArrayList để đảm bảo Thread-safe khi nhiều client kết nối/ngắt kết nối cùng lúc
    private static List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
    private static ExecutorService pool = Executors.newFixedThreadPool(50); // Hỗ trợ tối đa 50 client

    public AuctionServer(int port) {
        this.port = port;
    }

    // Phương thức bắt đầu chạy server (sẽ được gọi từ ServerApp)
    public void start() {
        System.out.println("[SERVER] Máy chủ đấu giá đang khởi động trên port " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[SERVER] Đang chờ Client kết nối...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] Client mới kết nối: " + clientSocket.getInetAddress());

                // Khởi tạo ClientHandler để xử lý luồng riêng cho client này
                ClientHandler clientThread = new ClientHandler(clientSocket);
                connectedClients.add(clientThread);
                pool.execute(clientThread);
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Lỗi khởi động server: " + e.getMessage());
        }
    }

    // Realtime Update (Observer): Gửi dữ liệu tới toàn bộ client
    public static void broadcast(Message message) {
        String jsonMessage = message.toJson();
        for (ClientHandler client : connectedClients) {
            client.sendMessage(jsonMessage);
        }
    }

    // Xóa client khi bị ngắt kết nối
    public static void removeClient(ClientHandler client) {
        connectedClients.remove(client);
        System.out.println("[SERVER] Một Client đã ngắt kết nối. Tổng số online: " + connectedClients.size());
    }
}