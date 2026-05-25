package com.auction.client.utils;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Quản lý kết nối TCP duy nhất tới Server.
 *
 * KIẾN TRÚC HAI LUỒNG:
 *   - listenerThread: đọc TẤT CẢ tin nhắn từ server liên tục (chạy nền).
 *   - Các message được phân loại:
 *       • Push message (BID_UPDATE, AUCTION_ENDED, TIME_EXTENDED)
 *         → dispatch tới pushListeners (Observer pattern)
 *       • Response message (SUCCESS/FAIL sau một lệnh)
 *         → đẩy vào responseQueue để sendRequest() lấy ra
 *
 * TẠI SAO KHÔNG DÙNG sendRequest() CŨ (gửi + đọc liền)?
 *   Server có thể gửi BID_UPDATE bất cứ lúc nào (không phải sau lệnh của mình).
 *   Nếu cả main thread và background thread cùng gọi in.readLine() → race condition.
 *   Giải pháp: chỉ listenerThread được đọc; phân loại rồi điều phối.
 */
public class ClientService {

    private static final String HOST = "localhost";
    private static final int PORT = 12345;

    // -------------------------------------------------------------------------
    // Thông tin phiên đăng nhập (static để toàn app chia sẻ)
    // -------------------------------------------------------------------------
    public static volatile String currentUsername = null;
    public static volatile String currentRole     = null;

    // -------------------------------------------------------------------------
    // Kết nối mạng
    // -------------------------------------------------------------------------
    private static Socket       socket;
    private static PrintWriter  out;
    private static BufferedReader in;

    // -------------------------------------------------------------------------
    // Hàng chờ response cho sendRequest() (request-response pattern)
    // -------------------------------------------------------------------------
    private static final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();

    // -------------------------------------------------------------------------
    // Lock để đảm bảo chỉ một request được gửi tại một thời điểm.
    // FIX: Tránh race condition khi LoadItemsThread (GET_ITEMS) và FX thread
    // (ADD_ITEM, PLACE_BID...) cùng chờ trên responseQueue → lấy nhầm response.
    // -------------------------------------------------------------------------
    private static final ReentrantLock requestLock = new ReentrantLock();

    // -------------------------------------------------------------------------
    // Danh sách listener cho push message từ server (Observer pattern)
    // CopyOnWriteArrayList: thread-safe khi iterate + add/remove từ thread khác
    // -------------------------------------------------------------------------
    private static final List<Consumer<String>> pushListeners = new CopyOnWriteArrayList<>();

    private static Thread listenerThread;

    // -------------------------------------------------------------------------
    // KẾT NỐI
    // -------------------------------------------------------------------------
    public static synchronized void connect() {
        if (socket != null && !socket.isClosed()) return;
        try {
            socket = new Socket(HOST, PORT);
            out    = new PrintWriter(socket.getOutputStream(), true);
            in     = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            startListenerThread();
            System.out.println("[ClientService] Đã kết nối tới Server " + HOST + ":" + PORT);
        } catch (IOException e) {
            System.err.println("[ClientService] Không thể kết nối: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // BACKGROUND LISTENER THREAD
    // -------------------------------------------------------------------------
    private static void startListenerThread() {
        listenerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;

                    // Push messages: server chủ động gửi, không phải response
                    // BUG FIX: Server gửi broadcast qua broadcastToItemWatchers()
                    // bằng Message.toJson() → format JSON: {"action":"BID_UPDATE","payload":"..."}
                    // Trước đây chỉ check startsWith("BID_UPDATE:") nên JSON không khớp,
                    // bị nhầm vào responseQueue → PlaceBidThread nhận nhầm → hiện "Đặt giá thất bại."
                    // Fix: thêm kiểm tra JSON push message để route đúng sang pushListeners.
                    if (msg.startsWith("BID_UPDATE:")
                            || msg.startsWith("AUCTION_ENDED:")
                            || msg.startsWith("TIME_EXTENDED:")
                            || isJsonPushMessage(msg)) {
                        for (Consumer<String> listener : pushListeners) {
                            listener.accept(msg);
                        }
                    } else {
                        // Response cho sendRequest() (SUCCESS/FAIL/...)
                        responseQueue.offer(msg);
                    }
                }
            } catch (IOException e) {
                if (socket != null && !socket.isClosed()) {
                    System.err.println("[ClientService] Mất kết nối với Server: " + e.getMessage());
                }
            }
        }, "ClientListenerThread");
        listenerThread.setDaemon(true); // tự tắt khi app đóng
        listenerThread.start();
    }

    // -------------------------------------------------------------------------
    // GỬI LỆNH VÀ NHẬN RESPONSE (request-response)
    //
    // FIX RACE CONDITION: Dùng ReentrantLock để đảm bảo chỉ một thread được
    // gửi request và chờ response tại một thời điểm.
    //
    // Vấn đề cũ: LoadItemsThread gửi GET_ITEMS và chờ responseQueue, trong khi
    // FX thread gửi ADD_ITEM và cũng chờ cùng responseQueue → hai thread cạnh
    // nhau lấy nhầm response của nhau → timeout → "CONNECTION_ERROR".
    //
    // Giải pháp: requestLock buộc các lệnh xếp hàng lần lượt. LoadItemsThread
    // giữ lock cho đến khi nhận xong GET_ITEMS response, sau đó ADD_ITEM mới
    // được gửi đi và nhận đúng response của nó.
    //
    // QUAN TRỌNG: Không bao giờ gọi sendRequest() trực tiếp từ JavaFX thread
    // vì nó block UI. Luôn dùng javafx.concurrent.Task (xem CreateItemController).
    // -------------------------------------------------------------------------
    public static String sendRequest(String message) {
        if (socket == null || socket.isClosed()) connect();
        if (out == null) return "CONNECTION_ERROR";

        requestLock.lock();
        try {
            // Xoá response thừa (nếu có) trước khi gửi lệnh mới,
            // phòng trường hợp có response stale chưa được consume.
            responseQueue.clear();
            out.println(message);
            String response = responseQueue.poll(10, TimeUnit.SECONDS);
            return response != null ? response : "CONNECTION_ERROR";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "CONNECTION_ERROR";
        } finally {
            requestLock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // ĐĂNG KÝ / HỦY LẮNG NGHE PUSH MESSAGE
    // Listener chạy trên listenerThread → phải dùng Platform.runLater() khi update UI
    // -------------------------------------------------------------------------
    public static void addPushListener(Consumer<String> listener) {
        pushListeners.add(listener);
    }

    public static void removePushListener(Consumer<String> listener) {
        pushListeners.remove(listener);
    }

    // -------------------------------------------------------------------------
    // ĐĂNG XUẤT (xóa session, không ngắt socket)
    // -------------------------------------------------------------------------
    public static void logout() {
        currentUsername = null;
        currentRole     = null;
    }

    // -------------------------------------------------------------------------
    // HELPER — nhận ra JSON push message từ server (gửi qua broadcastToItemWatchers)
    // Server serialize bằng Message.toJson() → {"action":"BID_UPDATE","payload":"..."}
    // Dùng string-search đơn giản, không cần parse JSON đầy đủ → nhanh, không throw.
    // -------------------------------------------------------------------------
    private static boolean isJsonPushMessage(String msg) {
        if (msg == null || !msg.startsWith("{")) return false;
        return msg.contains("\"BID_UPDATE\"")
                || msg.contains("\"AUCTION_ENDED\"")
                || msg.contains("\"TIME_EXTENDED\"");
    }

    // -------------------------------------------------------------------------
    // NGẮT KẾT NỐI (khi đóng app)
    // -------------------------------------------------------------------------
    public static void disconnect() {
        try {
            if (listenerThread != null) listenerThread.interrupt();
            if (in     != null) in.close();
            if (out    != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("[ClientService] Đã ngắt kết nối.");
        } catch (IOException e) {
            System.err.println("[ClientService] Lỗi khi ngắt kết nối: " + e.getMessage());
        }
    }
}