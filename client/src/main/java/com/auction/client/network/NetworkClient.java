package com.auction.client.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NetworkClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // Lắng nghe tín hiệu để báo cho giao diện (JavaFX) cập nhật
    private MessageListener messageListener;

    public NetworkClient(String serverAddress, int port, MessageListener listener) {
        this.messageListener = listener;
        try {
            socket = new Socket(serverAddress, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Khởi chạy luồng ngầm để liên tục nhận dữ liệu từ Server (Realtime Update)
            startListening();
        } catch (IOException e) {
            System.err.println("Không thể kết nối đến Server: " + e.getMessage());
        }
    }

    // Gửi tin nhắn hoặc lệnh (VD: Đặt giá) lên Server
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    // Luồng chạy ngầm để đọc dữ liệu Server gửi về
    private void startListening() {
        Thread listenThread = new Thread(() -> {
            try {
                String serverResponse;
                while ((serverResponse = in.readLine()) != null) {
                    System.out.println("Nhận từ Server: " + serverResponse);

                    // Báo cho giao diện biết có tin nhắn mới
                    if (messageListener != null) {
                        messageListener.onMessageReceived(serverResponse);
                    }
                }
            } catch (IOException e) {
                System.out.println("Mất kết nối tới Server.");
            }
        });
        listenThread.setDaemon(true); // Luồng sẽ tự tắt khi bạn tắt ứng dụng
        listenThread.start();
    }

    // Giao diện (Interface) để các lớp Controller của JavaFX sử dụng
    public interface MessageListener {
        void onMessageReceived(String message);
    }
}