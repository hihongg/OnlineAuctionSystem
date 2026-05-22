package com.auction.client.utils;

import java.io.*;
import java.net.Socket;

public class ClientService {

    private static final String HOST = "localhost";
    private static final int PORT = 12345;

    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;

    /**
     * Khởi tạo kết nối cứng đến Server.
     * Hàm này nên được gọi một lần duy nhất tại hàm main() khởi chạy ứng dụng Client.
     */
    public static void connect() {
        if (socket == null || socket.isClosed()) {
            try {
                socket = new Socket(HOST, PORT);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                System.out.println("[ClientService] Đã thiết lập kết nối liên tục với Server!");
            } catch (IOException e) {
                System.err.println("[ClientService] Không thể kết nối tới Server: " + e.getMessage());
            }
        }
    }

    /**
     * Gửi request và nhận phản hồi thông qua đường truyền có sẵn
     */
    public static String sendRequest(String message) {
        if (socket == null || socket.isClosed()) {
            connect();
        }

        try {
            if (out != null && in != null) {
                out.println(message);
                return in.readLine();
            }
        } catch (IOException e) {
            System.err.println("[ClientService] Lỗi gửi nhận dữ liệu: " + e.getMessage());
            return "CONNECTION_ERROR";
        }
        return "CONNECTION_ERROR";
    }

    /**
     * Ngắt kết nối an toàn khi người dùng đóng ứng dụng hoàn toàn
     */
    public static void disconnect() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("[ClientService] Đã đóng kết nối mạng.");
        } catch (IOException e) {
            System.err.println("[ClientService] Lỗi khi ngắt kết nối: " + e.getMessage());
        }
    }
}