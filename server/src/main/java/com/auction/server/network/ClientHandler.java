package com.auction.server.network;

import com.auction.server.dao.BidDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.services.AuctionService;
import com.auction.shared.models.Item;
import com.auction.shared.models.Message;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

/**
 * Xử lý giao tiếp với một Client cụ thể.
 *
 * GIAO THỨC (Protocol):
 *   Client → Server (plain text, dấu ":"  phân cách):
 *   ┌─────────────────────────────────────────────────────────────────────┐
 *   │  LỆNH               │  THAM SỐ                      │  AI GỌI    │
 *   ├─────────────────────┼───────────────────────────────┼────────────┤
 *   │  REGISTER           │  <user>:<pass>[:<role>]       │  Mọi người │
 *   │  LOGIN              │  <user>:<pass>                │  Mọi người │
 *   │  GET_ITEMS          │  (không có)                   │  Mọi người │
 *   │  GET_ITEM_BY_ID     │  <itemId>                     │  Đã login  │
 *   │  GET_ALL_ITEMS      │  (không có)                   │  ADMIN     │
 *   │  GET_MY_ITEMS       │  (không có)                   │  SELLER    │
 *   │  PLACE_BID          │  <itemId>:<amount>            │  BIDDER    │
 *   │  GET_BID_HISTORY    │  <itemId>                     │  Đã login  │
 *   │  ADD_ITEM           │  <name>:<desc>:<price>:<end>  │  SELLER    │
 *   │  UPDATE_ITEM        │  <id>:<name>:<desc>:<p>:<end> │  SELLER    │
 *   │  DELETE_ITEM        │  <itemId>                     │  SELLER    │
 *   └─────────────────────┴───────────────────────────────┴────────────┘
 *
 *   Server → Client:
 *     "SUCCESS"            – thao tác thành công, không có dữ liệu kèm
 *     "SUCCESS:<data>"     – thành công, data là JSON hoặc chuỗi mô tả
 *     "FAIL:<lý do>"       – thao tác thất bại kèm lý do
 *     "BID_UPDATE:<json>"  – broadcast khi có bid mới
 *     "AUCTION_ENDED:<msg>"– broadcast khi phiên đấu giá kết thúc
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private final AuctionServer server;
    private final AuctionService auctionService;

    // DAOs dùng trực tiếp trong handler
    private final UserDAO userDAO = new UserDAO();
    private final ItemDAO itemDAO = new ItemDAO();
    private final BidDAO bidDAO = new BidDAO();
    private final Gson gson = new Gson();

    // Lưu username sau khi đăng nhập để dùng cho PLACE_BID
    private String loggedInUsername = null;

    public ClientHandler(Socket socket, AuctionServer server, AuctionService auctionService) {
        this.socket = socket;
        this.server = server;
        this.auctionService = auctionService;
        try {
            this.in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("[HANDLER] Lỗi khởi tạo I/O: " + e.getMessage());
        }
    }

    // =========================================================================
    // VÒNG LẶP CHÍNH – đọc từng dòng từ client
    // =========================================================================
    @Override
    public void run() {
        try {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("[HANDLER] Nhận từ "
                        + socket.getInetAddress() + ": " + inputLine);
                handleRawMessage(inputLine.trim());
            }
        } catch (IOException e) {
            System.out.println("[HANDLER] Client ngắt kết nối: " + socket.getInetAddress());
        } finally {
            closeConnections();
        }
    }

    // =========================================================================
    // PHÂN LOẠI TIN NHẮN
    // Hỗ trợ cả plain-text "ACTION:arg1:arg2" và JSON Message object
    // =========================================================================
    private void handleRawMessage(String raw) {
        // Thử parse JSON trước (cho các message nâng cao sau này)
        if (raw.startsWith("{")) {
            try {
                Message msg = Message.fromJson(raw);
                handleJsonMessage(msg);
                return;
            } catch (Exception ignored) {
                // Không phải JSON hợp lệ → thử plain text
            }
        }

        // Plain text protocol: "ACTION:arg1:arg2:..."
        // split giới hạn 6 phần — đủ cho lệnh dài nhất (UPDATE_ITEM có 6 tham số).
        // Không dùng split không giới hạn để tránh tấn công DoS bằng chuỗi quá nhiều ":"
        String[] parts = raw.split(":", 6);
        String action = parts[0].toUpperCase();

        switch (action) {
            case "REGISTER":
                handleRegister(parts);
                break;
            case "LOGIN":
                handleLogin(parts);
                break;
            case "GET_ITEMS":
                handleGetItems();
                break;
            case "GET_ITEM_BY_ID":
                // Lấy chi tiết 1 sản phẩm — dùng cho màn hình ItemDetail
                handleGetItemById(parts);
                break;
            case "GET_ALL_ITEMS":
                // Lấy toàn bộ sản phẩm mọi trạng thái — chỉ dành cho Admin
                handleGetAllItems();
                break;
            case "PLACE_BID":
                handlePlaceBid(parts);
                break;
            case "GET_BID_HISTORY":
                handleGetBidHistory(parts);
                break;
            case "ADD_ITEM":
                handleAddItem(parts);
                break;
            case "UPDATE_ITEM":
                handleUpdateItem(parts);
                break;
            case "DELETE_ITEM":
                handleDeleteItem(parts);
                break;
            case "GET_MY_ITEMS":
                handleGetMyItems();
                break;
            default:
                sendMessage("FAIL:Lệnh không hỗ trợ: " + action);
                System.err.println("[HANDLER] Lệnh lạ: " + action);
        }
    }

    // =========================================================================
    // XỬ LÝ JSON MESSAGE (dùng cho broadcast ngược lại hoặc mở rộng sau)
    // =========================================================================
    private void handleJsonMessage(Message msg) {
        switch (msg.getAction().toUpperCase()) {
            case "PLACE_BID":
                // Payload JSON: {"itemId":1,"bidAmount":200.0}
                PlaceBidPayload payload = gson.fromJson(msg.getPayload(), PlaceBidPayload.class);
                processPlaceBid(payload.itemId, payload.bidAmount);
                break;
            case "GET_ITEMS":
                handleGetItems();
                break;
            default:
                sendMessage("FAIL:JSON action không hỗ trợ: " + msg.getAction());
        }
    }

    // =========================================================================
    // HANDLER: REGISTER
    // Format ngắn : "REGISTER:<username>:<password>"           → role = BIDDER
    // Format đầy đủ: "REGISTER:<username>:<password>:<role>"  → BIDDER hoặc SELLER
    //
    // LƯU Ý: Admin KHÔNG thể tự đăng ký — chỉ được tạo thủ công trong DB.
    // =========================================================================
    private void handleRegister(String[] parts) {
        if (parts.length < 3) {
            sendMessage("FAIL:Thiếu thông tin. Format: REGISTER:<username>:<password> hoặc REGISTER:<username>:<password>:<role>");
            return;
        }

        String username = parts[1].trim();
        String password = parts[2].trim();

        // --- Validate username & password ---
        if (username.isEmpty() || password.isEmpty()) {
            sendMessage("FAIL:Username và password không được để trống");
            return;
        }
        if (username.length() < 3 || username.length() > 50) {
            sendMessage("FAIL:Username phải từ 3–50 ký tự");
            return;
        }
        if (password.length() < 6) {
            sendMessage("FAIL:Password phải có ít nhất 6 ký tự");
            return;
        }

        // --- Xác định role (parts[3] nếu có, mặc định BIDDER) ---
        // split(":", 4) cho tối đa 4 phần → parts[3] là role nếu client gửi
        String role = "BIDDER";
        if (parts.length >= 4 && !parts[3].trim().isEmpty()) {
            role = parts[3].trim().toUpperCase();
        }

        // Chỉ cho phép BIDDER hoặc SELLER tự đăng ký; ADMIN phải tạo thủ công
        if (!role.equals("BIDDER") && !role.equals("SELLER")) {
            sendMessage("FAIL:Role không hợp lệ. Chỉ chấp nhận BIDDER hoặc SELLER");
            return;
        }

        // --- Ghi vào DB ---
        // Email tạm = username@auction.local (client hiện tại chưa gửi email riêng)
        String email = username + "@auction.local";
        boolean success = userDAO.registerUser(username, password, email, role);

        if (success) {
            loggedInUsername = username; // Tự động đăng nhập ngay sau đăng ký
            System.out.println("[HANDLER] Đăng ký thành công: " + username + " (" + role + ")");
            sendMessage("SUCCESS:" + role); // Trả role về để client hiển thị đúng giao diện
        } else {
            sendMessage("FAIL:Username đã tồn tại hoặc lỗi server");
        }
    }

    // =========================================================================
    // HANDLER: LOGIN
    // Format: "LOGIN:<username>:<password>"
    // =========================================================================
    private void handleLogin(String[] parts) {
        if (parts.length < 3) {
            sendMessage("FAIL:Thiếu thông tin đăng nhập");
            return;
        }
        String username = parts[1].trim();
        String password = parts[2].trim();

        boolean valid = userDAO.authenticateUser(username, password);

        if (valid) {
            loggedInUsername = username;

            // Lấy thêm role để client hiển thị đúng giao diện
            String[] userInfo = userDAO.getUserInfo(username);
            String role = (userInfo != null) ? userInfo[1] : "BIDDER";

            System.out.println("[HANDLER] Đăng nhập thành công: " + username + " (" + role + ")");
            sendMessage("SUCCESS:" + role); // Ví dụ: "SUCCESS:BIDDER"
        } else {
            sendMessage("FAIL:Sai username hoặc password");
        }
    }

    // =========================================================================
    // HANDLER: GET_ITEMS – danh sách sản phẩm đang RUNNING (dành cho Bidder)
    // Format: "GET_ITEMS"
    // Ai gọi được: mọi client (kể cả chưa đăng nhập — trang chủ hiển thị list)
    // =========================================================================
    private void handleGetItems() {
        List<Item> items = itemDAO.getActiveItems();
        String json = gson.toJson(items);
        sendMessage("SUCCESS:" + json);
        System.out.println("[HANDLER] Gửi " + items.size() + " sản phẩm RUNNING cho client.");
    }

    // =========================================================================
    // HANDLER: GET_ITEM_BY_ID – chi tiết 1 sản phẩm (dành cho màn hình detail)
    // Format: "GET_ITEM_BY_ID:<itemId>"
    // Ai gọi được: bất kỳ client đã đăng nhập
    //
    // Tại sao cần lệnh này thay vì dùng GET_ITEMS?
    //   GET_ITEMS chỉ trả RUNNING items — sau khi phiên FINISHED,
    //   Bidder vẫn cần xem kết quả (người thắng, giá cuối).
    //   GET_ITEM_BY_ID trả item bất kể trạng thái.
    // =========================================================================
    private void handleGetItemById(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            sendMessage("FAIL:Thiếu itemId. Format: GET_ITEM_BY_ID:<itemId>");
            return;
        }
        try {
            int itemId = Integer.parseInt(parts[1].trim());
            Item item = itemDAO.getItemById(itemId);

            if (item != null) {
                sendMessage("SUCCESS:" + gson.toJson(item));
                System.out.println("[HANDLER] Gửi chi tiết item #" + itemId
                        + " (" + item.getName() + ") cho " + loggedInUsername);
            } else {
                sendMessage("FAIL:Không tìm thấy sản phẩm #" + itemId);
            }
        } catch (NumberFormatException e) {
            sendMessage("FAIL:itemId phải là số nguyên");
        }
    }

    // =========================================================================
    // HANDLER: GET_ALL_ITEMS – toàn bộ sản phẩm mọi trạng thái (chỉ Admin)
    // Format: "GET_ALL_ITEMS"
    // Ai gọi được: ADMIN
    //
    // Trả về list gồm tất cả status: OPEN, RUNNING, FINISHED, PAID, CANCELED
    // → Admin dùng để quản lý, thống kê, hoặc can thiệp thủ công.
    // =========================================================================
    private void handleGetAllItems() {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }

        // Kiểm tra quyền Admin
        String[] info = userDAO.getUserInfo(loggedInUsername);
        if (info == null || !info[1].equals("ADMIN")) {
            sendMessage("FAIL:Chỉ Admin mới có quyền xem toàn bộ danh sách sản phẩm");
            return;
        }

        List<Item> allItems = itemDAO.getAllItems();
        sendMessage("SUCCESS:" + gson.toJson(allItems));
        System.out.println("[HANDLER] Admin " + loggedInUsername
                + " lấy toàn bộ " + allItems.size() + " sản phẩm.");
    }

    // =========================================================================
    // HANDLER: PLACE_BID (plain text)
    // Format: "PLACE_BID:<itemId>:<bidAmount>"
    // =========================================================================
    private void handlePlaceBid(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (parts.length < 3) {
            sendMessage("FAIL:Thiếu thông tin đặt giá");
            return;
        }

        try {
            int itemId      = Integer.parseInt(parts[1].trim());
            double bidAmount = Double.parseDouble(parts[2].trim());
            processPlaceBid(itemId, bidAmount);
        } catch (NumberFormatException e) {
            sendMessage("FAIL:itemId hoặc bidAmount không hợp lệ");
        }
    }

    /**
     * Logic đặt giá dùng chung cho cả plain-text và JSON handler.
     *
     * Luồng xử lý:
     *   1. BidDAO.placeBidTransaction() – kiểm tra giá, khóa DB (FOR UPDATE),
     *      lưu bid_history, cập nhật items — tất cả trong 1 Transaction.
     *   2. Nếu thành công → AuctionService xử lý anti-sniping + broadcast
     *      cho tất cả client đang kết nối.
     */
    private void processPlaceBid(int itemId, double bidAmount) {

        // Bước 1: Giao dịch DB an toàn (Transaction + Pessimistic Lock)
        String result = bidDAO.placeBidTransaction(itemId, loggedInUsername, bidAmount);

        if ("SUCCESS".equals(result)) {
            // Bước 2: Anti-sniping + broadcast realtime cho mọi client
            try {
                auctionService.handlePostBidSuccess(itemId, loggedInUsername, bidAmount, server);
            } catch (Exception e) {
                // Broadcast lỗi không làm hỏng kết quả đặt giá — chỉ log
                System.err.println("[HANDLER] Lỗi broadcast sau bid: " + e.getMessage());
            }

            sendMessage("SUCCESS:Đặt giá thành công $" + bidAmount);
            System.out.println("[HANDLER] " + loggedInUsername
                    + " đặt $" + bidAmount + " cho item #" + itemId);
        } else {
            // result là chuỗi "ERROR: ..." từ BidDAO
            sendMessage("FAIL:" + result.replace("ERROR: ", ""));
            System.err.println("[HANDLER] Đặt giá thất bại: " + result);
        }
    }

    // =========================================================================
    // GỬI TIN NHẮN VỀ CLIENT
    // =========================================================================
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    // =========================================================================
    // HANDLER: GET_BID_HISTORY – lịch sử đặt giá dùng cho biểu đồ realtime
    // Format: "GET_BID_HISTORY:<itemId>"
    // =========================================================================
    private void handleGetBidHistory(String[] parts) {
        if (parts.length < 2) {
            sendMessage("FAIL:Thiếu itemId");
            return;
        }
        try {
            int itemId = Integer.parseInt(parts[1].trim());
            List<java.util.Map<String, Object>> history = bidDAO.getBidHistory(itemId);
            sendMessage("SUCCESS:" + gson.toJson(history));
        } catch (NumberFormatException e) {
            sendMessage("FAIL:itemId không hợp lệ");
        }
    }

    // =========================================================================
    // HANDLER: ADD_ITEM – Seller thêm sản phẩm mới
    // Format: "ADD_ITEM:<name>:<description>:<startingPrice>:<endTimeMs>"
    // =========================================================================
    private void handleAddItem(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        // Lấy role để xác minh Seller/Admin
        String[] info = userDAO.getUserInfo(loggedInUsername);
        if (info == null || (!info[1].equals("SELLER") && !info[1].equals("ADMIN"))) {
            sendMessage("FAIL:Chỉ Seller hoặc Admin mới được thêm sản phẩm");
            return;
        }
        if (parts.length < 5) {
            sendMessage("FAIL:Format: ADD_ITEM:<name>:<description>:<startingPrice>:<endTimeMs>");
            return;
        }
        try {
            String name         = parts[1].trim();
            String description  = parts[2].trim();
            double startPrice   = Double.parseDouble(parts[3].trim());
            long   endTime      = Long.parseLong(parts[4].trim());
            int    sellerId     = userDAO.getUserIdByUsername(loggedInUsername);

            Item newItem = new Item(name, description, startPrice, endTime, sellerId);
            int newId = itemDAO.addItem(newItem);

            if (newId > 0) {
                sendMessage("SUCCESS:Đã thêm sản phẩm #" + newId);
                System.out.println("[HANDLER] " + loggedInUsername + " thêm item #" + newId + ": " + name);
            } else {
                sendMessage("FAIL:Không thể thêm sản phẩm. Kiểm tra lại dữ liệu.");
            }
        } catch (NumberFormatException e) {
            sendMessage("FAIL:startingPrice hoặc endTimeMs không hợp lệ");
        }
    }

    // =========================================================================
    // HANDLER: UPDATE_ITEM – Seller sửa thông tin sản phẩm (chỉ khi còn OPEN)
    // Format: "UPDATE_ITEM:<itemId>:<name>:<description>:<startingPrice>:<endTimeMs>"
    // =========================================================================
    private void handleUpdateItem(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        String[] info = userDAO.getUserInfo(loggedInUsername);
        if (info == null || (!info[1].equals("SELLER") && !info[1].equals("ADMIN"))) {
            sendMessage("FAIL:Chỉ Seller hoặc Admin mới được sửa sản phẩm");
            return;
        }
        if (parts.length < 6) {
            sendMessage("FAIL:Format: UPDATE_ITEM:<itemId>:<name>:<description>:<startingPrice>:<endTimeMs>");
            return;
        }
        try {
            int    itemId      = Integer.parseInt(parts[1].trim());
            String name        = parts[2].trim();
            String description = parts[3].trim();
            double startPrice  = Double.parseDouble(parts[4].trim());
            long   endTime     = Long.parseLong(parts[5].trim());

            boolean ok = itemDAO.updateItem(itemId, name, description, startPrice, endTime);
            if (ok) {
                sendMessage("SUCCESS:Đã cập nhật sản phẩm #" + itemId);
            } else {
                sendMessage("FAIL:Không thể sửa. Phiên có thể đã RUNNING hoặc không tồn tại.");
            }
        } catch (NumberFormatException e) {
            sendMessage("FAIL:Dữ liệu số không hợp lệ");
        }
    }

    // =========================================================================
    // HANDLER: DELETE_ITEM – xóa sản phẩm (chỉ khi OPEN/FINISHED/CANCELED)
    // Format: "DELETE_ITEM:<itemId>"
    // =========================================================================
    private void handleDeleteItem(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        String[] info = userDAO.getUserInfo(loggedInUsername);
        if (info == null || (!info[1].equals("SELLER") && !info[1].equals("ADMIN"))) {
            sendMessage("FAIL:Chỉ Seller hoặc Admin mới được xóa sản phẩm");
            return;
        }
        if (parts.length < 2) {
            sendMessage("FAIL:Thiếu itemId");
            return;
        }
        try {
            int itemId = Integer.parseInt(parts[1].trim());
            boolean ok = itemDAO.deleteItem(itemId);
            if (ok) {
                sendMessage("SUCCESS:Đã xóa sản phẩm #" + itemId);
            } else {
                sendMessage("FAIL:Không thể xóa. Phiên đang RUNNING hoặc không tồn tại.");
            }
        } catch (NumberFormatException e) {
            sendMessage("FAIL:itemId không hợp lệ");
        }
    }

    // =========================================================================
    // HANDLER: GET_MY_ITEMS – Seller xem danh sách sản phẩm của mình
    // Format: "GET_MY_ITEMS"
    // =========================================================================
    private void handleGetMyItems() {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        int sellerId = userDAO.getUserIdByUsername(loggedInUsername);
        if (sellerId < 0) {
            sendMessage("FAIL:Không tìm thấy thông tin người dùng");
            return;
        }
        List<Item> myItems = itemDAO.getItemsBySeller(sellerId);
        sendMessage("SUCCESS:" + gson.toJson(myItems));
    }

    // =========================================================================
    // DỌN DẸP KẾT NỐI
    // =========================================================================
    private void closeConnections() {
        try {
            if (server != null) server.removeClient(this);
            if (in    != null) in.close();
            if (out   != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // Inner class – dùng để deserialize payload JSON của PLACE_BID
    // =========================================================================
    private static class PlaceBidPayload {
        int    itemId;
        double bidAmount;
    }
}