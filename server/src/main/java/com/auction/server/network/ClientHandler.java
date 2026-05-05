package com.auction.server.network;

import com.auction.shared.models.Message;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ClientHandler(Socket socket) {
        this.socket = socket;
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
                System.out.println("[SERVER] Nhận được: " + inputLine);

                // Giải mã JSON thành đối tượng Message để server hiểu
                try {
                    Message msg = Message.fromJson(inputLine);
                    // TODO: Gửi msg này cho AuctionService để xử lý (Login, Bid, v.v.)
                } catch (Exception e) {
                    System.err.println("[SERVER] Tin nhắn không đúng định dạng JSON: " + inputLine);
                }
            }
        } catch (IOException e) {
            System.out.println("[SERVER] Một Client đã ngắt kết nối.");
        } finally {
            closeConnections();
        }
    }

    // Phương thức này CỰC KỲ QUAN TRỌNG để AuctionServer gọi khi muốn Broadcast
    public void sendMessage(String jsonMessage) {
        if (out != null) {
            out.println(jsonMessage);
        }
    }

    // Dọn dẹp tài nguyên và báo cho Server biết để xóa khỏi danh sách
    private void closeConnections() {
        try {
            AuctionServer.removeClient(this);
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}