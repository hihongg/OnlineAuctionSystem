package com.auction.server.network;

import com.auction.shared.models.Message;
import com.auction.server.services.AuctionService; // Import Service của bạn
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuctionServer {
    private int port;

    // Bỏ static để thiết kế thuần OOP hơn
    private List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
    private ExecutorService pool = Executors.newFixedThreadPool(50);

    // Thêm AuctionService để dùng chung cho mọi Client
    private AuctionService auctionService;

    public AuctionServer(int port) {
        this.port = port;
        // Khởi tạo Service, truyền chính Server này vào để Service có thể gọi hàm broadcast()
        this.auctionService = new AuctionService(this);
    }

    public void start() {
        System.out.println("[SERVER] Máy chủ đấu giá đang khởi động trên port " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[SERVER] Đang chờ Client kết nối...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] Client mới kết nối: " + clientSocket.getInetAddress());

                // Truyền 'this' (AuctionServer) và auctionService vào ClientHandler
                ClientHandler clientThread = new ClientHandler(clientSocket, this, auctionService);
                connectedClients.add(clientThread);
                pool.execute(clientThread);
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Lỗi khởi động server: " + e.getMessage());
        }
    }

    // Bỏ static
    public void broadcast(Message message) {
        String jsonMessage = message.toJson();
        for (ClientHandler client : connectedClients) {
            client.sendMessage(jsonMessage);
        }
    }

    // Bỏ static
    public void removeClient(ClientHandler client) {
        connectedClients.remove(client);
        System.out.println("[SERVER] Một Client đã ngắt kết nối. Tổng số online: " + connectedClients.size());
    }
}