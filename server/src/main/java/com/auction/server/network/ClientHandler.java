package com.auction.server.network;

import com.auction.shared.models.Message;
import com.auction.server.services.AuctionService; // Thêm import Service
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // Thêm các biến lưu trữ Server và Service
    private AuctionServer server;
    private AuctionService auctionService;

    // Cập nhật Constructor để nhận Server và Service
    public ClientHandler(Socket socket, AuctionServer server, AuctionService auctionService) {
        this.socket = socket;
        this.server = server;
        this.auctionService = auctionService;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("[SERVER] Lỗi khởi tạo luồng I/O cho client: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String inputLine;
            // Liên tục lắng nghe tin nhắn từ Client gửi lên
            while ((inputLine = in.readLine()) != null) {
                System.out.println("[SERVER] Nhận được từ " + socket.getInetAddress() + ": " + inputLine);

                // Giải mã JSON thành đối tượng Message
                try {
                    Message msg = Message.fromJson(inputLine);
                    // Gọi hàm xử lý tin nhắn
                    handleIncomingMessage(msg);
                } catch (Exception e) {
                    System.err.println("[SERVER] Tin nhắn không đúng định dạng JSON: " + inputLine);
                }
            }
        } catch (IOException e) {
            System.out.println("[SERVER] Một Client đã ngắt kết nối đột ngột.");
        } finally {
            closeConnections();
        }
    }

    // Hàm mới: Phân loại và xử lý tin nhắn
    private void handleIncomingMessage(Message msg) {
        // Giả sử class Message của bạn có thuộc tính 'type' (vd: "LOGIN", "PLACE_BID")
        String type = msg.getAction();

        switch (type) {
            case "PLACE_BID":
                // Rút trích dữ liệu từ message và gọi Service
                // Ví dụ: int itemId = msg.getInt("itemId");
                // String response = auctionService.processBid(itemId, userId, bidAmount);
                // sendMessage(response);
                System.out.println("[SERVER] Đang xử lý yêu cầu đặt giá...");
                break;

            case "LOGIN":
                // Xử lý đăng nhập qua UserDao/AuctionService
                System.out.println("[SERVER] Đang xử lý đăng nhập...");
                break;

            default:
                System.out.println("[SERVER] Loại tin nhắn không được hỗ trợ: " + type);
        }
    }

    public void sendMessage(String jsonMessage) {
        if (out != null) {
            out.println(jsonMessage);
        }
    }

    private void closeConnections() {
        try {
            // Thay vì gọi static, ta gọi phương thức của đối tượng server
            if (server != null) {
                server.removeClient(this);
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}