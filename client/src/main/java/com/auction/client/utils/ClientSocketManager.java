package com.auction.client.utils;

import com.auction.shared.models.Message;
import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Quản lý kết nối Socket DUY NHẤT (Singleton) giữa Client và Server.
 *
 * ============================================================
 * TẠI SAO CẦN CLASS NÀY? (thay thế ClientService cũ)
 * ============================================================
 * ClientService cũ: mở Socket mới → gửi lệnh → đọc 1 dòng → đóng Socket.
 *   Hậu quả: server broadcast BID_UPDATE đến socket đã đóng rồi
 *   → client không bao giờ nhận được update giá realtime!
 *
 * ClientSocketManager: mở Socket 1 lần khi app khởi động, giữ mãi.
 *   Listener thread chạy ngầm đọc mọi tin nhắn đến:
 *     - Nếu là JSON (broadcast) → gọi các callback đã đăng ký
 *     - Nếu là plain text (response) → đẩy vào queue để sendRequest() lấy
 *
 * ============================================================
 * SƠ ĐỒ LUỒNG DỮ LIỆU
 * ============================================================
 *
 *  Controller          ClientSocketManager            Server
 *      │                       │                        │
 *      │  sendRequest("BID")   │                        │
 *      │──────────────────────►│──── out.println() ────►│
 *      │                       │                        │
 *      │                       │◄─── "SUCCESS:..." ─────│  (response)
 *      │◄──── "SUCCESS:..." ───│  (responseQueue)       │
 *      │                       │                        │
 *      │                       │◄─── {"action":"BID_UPDATE",...} ──│  (broadcast)
 *      │◄── listener.accept() ─│  (Platform.runLater)   │
 *      │   (cập nhật UI)       │                        │
 *
 * ============================================================
 * CÁCH DÙNG (trong Controller)
 * ============================================================
 *
 * 1. Gửi request và lấy response (đồng bộ):
 *
 *   String response = ClientSocketManager.getInstance()
 *                         .sendRequest("LOGIN:alice:pass123");
 *   if (response.startsWith("SUCCESS")) { ... }
 *
 * 2. Đăng ký nhận broadcast realtime (trong initialize()):
 *
 *   private final Consumer<Message> bidListener = this::onBidUpdate;
 *
 *   public void initialize(...) {
 *       ClientSocketManager.getInstance().addBroadcastListener(bidListener);
 *   }
 *
 *   private void onBidUpdate(Message msg) {
 *       if ("BID_UPDATE".equals(msg.getAction())) {
 *           Item updated = gson.fromJson(msg.getPayload(), Item.class);
 *           lblPrice.setText("Giá hiện tại: $" + updated.getCurrentHighestBid());
 *       }
 *   }
 *
 * 3. Hủy đăng ký khi màn hình đóng (tránh memory leak):
 *
 *   // Gọi trong Stage.setOnHiding() hoặc khi chuyển màn hình
 *   ClientSocketManager.getInstance().removeBroadcastListener(bidListener);
 */
public class ClientSocketManager {

    // =========================================================================
    // CẤU HÌNH KẾT NỐI
    // =========================================================================
    private static final String DEFAULT_HOST         = "localhost";
    private static final int    DEFAULT_PORT         = 12345;

    /** Thời gian tối đa chờ response từ server trước khi trả về "TIMEOUT" */
    private static final int    RESPONSE_TIMEOUT_SEC = 10;

    // =========================================================================
    // SINGLETON — Double-checked locking (thread-safe)
    // =========================================================================
    private static volatile ClientSocketManager instance;

    public static ClientSocketManager getInstance() {
        if (instance == null) {
            synchronized (ClientSocketManager.class) {
                if (instance == null) {
                    instance = new ClientSocketManager();
                }
            }
        }
        return instance;
    }

    // Constructor private → không tạo instance bên ngoài
    private ClientSocketManager() {}

    // =========================================================================
    // STATE NỘI BỘ
    // =========================================================================
    private Socket         socket;
    private PrintWriter    out;
    private BufferedReader in;

    /** true khi socket đang mở và listener thread đang chạy */
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * Hàng đợi nhận response của sendRequest().
     * Listener thread đẩy vào, sendRequest() poll ra.
     * LinkedBlockingQueue thread-safe, hỗ trợ poll với timeout.
     */
    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();

    /**
     * Danh sách callbacks nhận broadcast.
     * CopyOnWriteArrayList: an toàn khi Controller add/remove trong khi
     * listener thread đang duyệt qua list.
     */
    private final List<Consumer<Message>> broadcastListeners = new CopyOnWriteArrayList<>();

    // =========================================================================
    // KẾT NỐI ĐẾN SERVER
    // =========================================================================

    /**
     * Kết nối tới server với host/port mặc định (localhost:12345).
     * Gọi một lần duy nhất khi ứng dụng khởi động.
     *
     * @throws IOException nếu server chưa chạy hoặc không thể kết nối
     */
    public void connect() throws IOException {
        connect(DEFAULT_HOST, DEFAULT_PORT);
    }

    /**
     * Kết nối tới server với host/port tùy chỉnh.
     * Nếu đã kết nối rồi thì bỏ qua.
     */
    public void connect(String host, int port) throws IOException {
        if (connected.get()) {
            System.out.println("[SOCKET] Đã kết nối, bỏ qua lệnh connect().");
            return;
        }

        socket = new Socket(host, port);
        out    = new PrintWriter(socket.getOutputStream(), true);
        in     = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        connected.set(true);

        startListenerThread();
        System.out.println("[SOCKET] Kết nối thành công tới " + host + ":" + port);
    }

    // =========================================================================
    // GỬI REQUEST — CHỜ RESPONSE (blocking, timeout 10 giây)
    // =========================================================================

    /**
     * Gửi lệnh tới server theo plain-text protocol và trả về response.
     *
     * Phương thức này BLOCKING: thread gọi sẽ chờ cho đến khi
     * nhận được response hoặc hết timeout.
     * → Không gọi trên JavaFX Application Thread! Dùng Task hoặc new Thread().
     *
     * @param request lệnh gửi đi, VD: "LOGIN:alice:pass123"
     * @return response từ server, hoặc "TIMEOUT" / "NOT_CONNECTED"
     */
    public String sendRequest(String request) {
        if (!connected.get()) {
            System.err.println("[SOCKET] Chưa kết nối. Gọi connect() trước.");
            return "NOT_CONNECTED";
        }

        // Xóa response cũ còn sót để tránh nhận nhầm
        responseQueue.clear();

        // Gửi lệnh tới server
        out.println(request);
        System.out.println("[SOCKET] >>> Gửi : " + request);

        try {
            // Chờ response trong hàng đợi (tối đa RESPONSE_TIMEOUT_SEC giây)
            String response = responseQueue.poll(RESPONSE_TIMEOUT_SEC, TimeUnit.SECONDS);

            if (response == null) {
                System.err.println("[SOCKET] TIMEOUT! Không nhận được response cho: " + request);
                return "TIMEOUT";
            }

            System.out.println("[SOCKET] <<< Nhận: " + response);
            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[SOCKET] Thread bị interrupt khi chờ response.");
            return "INTERRUPTED";
        }
    }

    // =========================================================================
    // ĐĂNG KÝ / HỦY BROADCAST LISTENER
    // =========================================================================

    /**
     * Đăng ký callback để nhận broadcast từ server.
     *
     * Server broadcast các sự kiện:
     *   - action = "BID_UPDATE"    → payload là JSON của Item mới nhất
     *   - action = "AUCTION_ENDED" → payload là thông báo kết thúc
     *   - action = "TIME_EXTENDED" → payload là JSON {"itemId":1,"newEndTime":...}
     *
     * Callback LUÔN được gọi trên JavaFX Application Thread (an toàn update UI).
     *
     * @param listener Consumer nhận Message broadcast
     */
    public void addBroadcastListener(Consumer<Message> listener) {
        broadcastListeners.add(listener);
        System.out.println("[SOCKET] Đã đăng ký listener. Tổng: " + broadcastListeners.size());
    }

    /**
     * Hủy đăng ký listener khi màn hình đóng.
     * Quan trọng: gọi cái này để tránh memory leak và cập nhật sai màn hình cũ.
     *
     * @param listener đúng object đã truyền vào addBroadcastListener()
     */
    public void removeBroadcastListener(Consumer<Message> listener) {
        broadcastListeners.remove(listener);
        System.out.println("[SOCKET] Đã hủy listener. Còn lại: " + broadcastListeners.size());
    }

    // =========================================================================
    // LISTENER THREAD — chạy ngầm, đọc mọi tin nhắn đến từ server
    // =========================================================================
    private void startListenerThread() {
        Thread listenerThread = new Thread(() -> {
            try {
                String line;
                while (connected.get() && (line = in.readLine()) != null) {
                    processIncomingMessage(line.trim());
                }
            } catch (IOException e) {
                if (connected.get()) {
                    // Mất kết nối đột ngột (server tắt, mạng đứt...)
                    System.err.println("[SOCKET] Mất kết nối với server: " + e.getMessage());
                    connected.set(false);
                    // TODO (nâng cao): thêm logic reconnect tự động ở đây
                }
            }
            System.out.println("[SOCKET] Listener thread đã dừng.");
        }, "ClientListenerThread");

        listenerThread.setDaemon(true); // Tự tắt khi JVM shutdown
        listenerThread.start();
        System.out.println("[SOCKET] Listener thread đã khởi động.");
    }

    /**
     * Phân loại và xử lý từng tin nhắn đến:
     *
     *   Tin nhắn bắt đầu bằng '{'  → JSON → broadcast → gọi listeners
     *   Còn lại (plain text)        → response → đẩy vào responseQueue
     *
     * Phân biệt này hoạt động vì server tuân thủ protocol:
     *   - Response: "SUCCESS:..." hoặc "FAIL:..."
     *   - Broadcast: {"action":"BID_UPDATE","payload":"..."}
     */
    private void processIncomingMessage(String message) {
        if (message.isEmpty()) return;

        if (message.startsWith("{")) {
            // --- BROADCAST từ server ---
            try {
                Message msg = Message.fromJson(message);
                // Gọi listeners trên JavaFX thread để Controller cập nhật UI trực tiếp
                Platform.runLater(() -> {
                    for (Consumer<Message> listener : broadcastListeners) {
                        try {
                            listener.accept(msg);
                        } catch (Exception e) {
                            // Lỗi trong 1 listener không được làm hỏng các listener khác
                            System.err.println("[SOCKET] Lỗi trong listener: " + e.getMessage());
                        }
                    }
                });
                System.out.println("[SOCKET] <<< Broadcast: " + msg.getAction());
            } catch (Exception e) {
                System.err.println("[SOCKET] Không parse được JSON: " + message);
            }
        } else {
            // --- RESPONSE của sendRequest() ---
            // offer() không block (queue không giới hạn size)
            responseQueue.offer(message);
        }
    }

    // =========================================================================
    // KIỂM TRA TRẠNG THÁI & DỌN DẸP
    // =========================================================================

    /** @return true nếu socket đang mở và sẵn sàng gửi/nhận */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Đóng kết nối an toàn. Gọi trong ClientApp.stop() khi ứng dụng tắt.
     */
    public void disconnect() {
        connected.set(false);
        try {
            if (in     != null) in.close();
            if (out    != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("[SOCKET] Đã ngắt kết nối.");
        } catch (IOException e) {
            System.err.println("[SOCKET] Lỗi khi đóng socket: " + e.getMessage());
        }
    }
}