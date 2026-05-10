package com.auction.server.dao.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

// Triển khai Runnable để chạy trên một luồng (Thread) riêng biệt
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
            System.err.println("Lỗi khởi tạo I/O cho client: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String clientMessage;
            // Vòng lặp liên tục lắng nghe tín hiệu từ Client này
            while ((clientMessage = in.readLine()) != null) {
                System.out.println("Nhận từ Client: " + clientMessage);

                // Tạm thời phản hồi lại để test kết nối
                out.println("Server đã nhận: " + clientMessage);
            }
        } catch (IOException e) {
            System.out.println("Client ngắt kết nối.");
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}