package com.auction.server.network;

import com.auction.server.dao.BidDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.services.AuctionService;
import com.auction.shared.models.Item;
import com.auction.shared.models.ItemFactory;
import com.auction.shared.models.Message;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
 *   │  WATCH_ITEM         │  <itemId>                     │  Đã login  │
 *   │  UNWATCH_ITEM       │  (không có)                   │  Đã login  │
 *   │  GET_ALL_ITEMS      │  (không có)                   │  ADMIN     │
 *   │  GET_ALL_USERS      │  (không có)                   │  ADMIN     │
 *   │  DELETE_USER        │  <username>                   │  ADMIN     │
 *   │  UPDATE_USER_ROLE   │  <username>:<newRole>         │  ADMIN     │
 *   │  CHANGE_ITEM_STATUS │  <itemId>:<status>            │  ADMIN     │
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
 *     "BID_UPDATE:<json>"  – broadcast khi có bid mới (chỉ đến watcher của item đó)
 *     "TIME_EXTENDED:<json>"– broadcast khi phiên được gia hạn (anti-sniping)
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

    // Lưu thông tin sau khi đăng nhập (username + role).
    private String loggedInUsername = null;
    private String loggedInRole     = null;

    /**
     * ID của item mà client đang theo dõi realtime (màn hình ItemDetail).
     */
    private volatile int watchedItemId = -1;

    /** Getter cho AuctionServer.broadcastToItemWatchers() */
    public int getWatchedItemId() {
        return watchedItemId;
    }

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
                        + socket.getInetAddress() + ": "
                        + (inputLine.length() > 200 ? inputLine.substring(0, 200) + "...[truncated]" : inputLine));
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
    // =========================================================================
    private void handleRawMessage(String raw) {
        if (raw.startsWith("{")) {
            try {
                Message msg = Message.fromJson(raw);
                handleJsonMessage(msg);
                return;
            } catch (Exception ignored) {
            }
        }

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
                handleGetItemById(parts);
                break;
            case "WATCH_ITEM":
                handleWatchItem(parts);
                break;
            case "UNWATCH_ITEM":
                watchedItemId = -1;
                sendMessage("SUCCESS:Đã hủy theo dõi phiên đấu giá");
                break;
            case "GET_ALL_ITEMS":
                handleGetAllItems();
                break;
            case "GET_ALL_USERS":
                handleGetAllUsers();
                break;
            case "DELETE_USER":
                handleDeleteUser(parts);
                break;
            case "UPDATE_USER_ROLE":
                handleUpdateUserRole(parts);
                break;
            case "CHANGE_ITEM_STATUS":
                handleChangeItemStatus(parts);
                break;
            case "PLACE_BID":
                handlePlaceBid(parts);
                break;
            case "GET_BID_HISTORY":
                handleGetBidHistory(parts);
                break;
            case "ADD_ITEM":
                handleAddItem(raw);
                break;
            case "UPDATE_ITEM":
                handleUpdateItem(parts);
                break;
            case "DELETE_ITEM":
                handleDeleteItem(parts);
                break;
            case "ADMIN_DELETE_FINISHED":
                handleAdminDeleteFinished();
                break;
            case "GET_MY_ITEMS":
                handleGetMyItems();
                break;
            case "GET_WON_ITEMS":
                handleGetWonItems();
                break;
            case "AUTO_BID":
                handleAutoBid(parts);
                break;
            case "CANCEL_AUTO_BID":
                handleCancelAutoBid(parts);
                break;
            case "GET_BALANCE":
                handleGetBalance();
                break;
            case "DEPOSIT":
                handleDeposit(parts);
                break;
            case "DEPOSIT_REQUEST":
                handleDepositRequest(parts);
                break;
            case "ADMIN_DEPOSIT":
                handleAdminDeposit(parts);
                break;
            case "GET_DEPOSIT_REQUESTS":
                handleGetDepositRequests();
                break;
            case "APPROVE_DEPOSIT":
                handleApproveDeposit(parts);
                break;
            case "REJECT_DEPOSIT":
                handleRejectDeposit(parts);
                break;
            case "ADJUST_BALANCE":
                handleAdjustBalance(parts);
                break;
            default:
                sendMessage("FAIL:Lệnh không hỗ trợ: " + action);
                System.err.println("[HANDLER] Lệnh lạ: " + action);
        }
    }

    // =========================================================================
    // XỬ LÝ JSON MESSAGE
    // =========================================================================
    private void handleJsonMessage(Message msg) {
        switch (msg.getAction().toUpperCase()) {
            case "PLACE_BID":
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
    // =========================================================================
    private void handleRegister(String[] parts) {
        if (parts.length < 3) {
            sendMessage("FAIL:Thiếu thông tin. Format: REGISTER:<username>:<password> hoặc REGISTER:<username>:<password>:<role>");
            return;
        }

        String username = parts[1].trim();
        String password = parts[2].trim();

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

        String role = "BIDDER";
        if (parts.length >= 4 && !parts[3].trim().isEmpty()) {
            role = parts[3].trim().toUpperCase();
        }

        if (!role.equals("BIDDER") && !role.equals("SELLER")) {
            sendMessage("FAIL:Role không hợp lệ. Chỉ chấp nhận BIDDER hoặc SELLER");
            return;
        }

        String email = username + "@auction.local";
        boolean success = userDAO.registerUser(username, password, email, role);

        if (success) {
            loggedInUsername = username;
            loggedInRole     = role;
            System.out.println("[HANDLER] Đăng ký thành công: " + username + " (" + role + ")");
            sendMessage("SUCCESS:" + role);
        } else {
            sendMessage("FAIL:Username đã tồn tại hoặc lỗi server");
        }
    }

    // =========================================================================
    // HANDLER: LOGIN
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
            String[] userInfo = userDAO.getUserInfo(username);
            String role = (userInfo != null) ? userInfo[1] : "BIDDER";

            loggedInUsername = username;
            loggedInRole     = role;

            System.out.println("[HANDLER] Đăng nhập thành công: " + username + " (" + role + ")");
            sendMessage("SUCCESS:" + role);
        } else {
            sendMessage("FAIL:Sai username hoặc password");
        }
    }

    // =========================================================================
    // HANDLER: GET_ITEMS
    // =========================================================================
    private void handleGetItems() {
        List<Item> items = itemDAO.getActiveItems();
        String json = gson.toJson(items);
        sendMessage("SUCCESS:" + json);
        System.out.println("[HANDLER] Gửi " + items.size() + " sản phẩm RUNNING cho client.");
    }

    // =========================================================================
    // HANDLER: GET_ITEM_BY_ID
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
    // HANDLER: GET_ALL_ITEMS (Admin only)
    // =========================================================================
    private void handleGetAllItems() {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (!"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Admin mới có quyền xem toàn bộ danh sách sản phẩm");
            return;
        }

        List<Item> allItems = itemDAO.getAllItems();
        sendMessage("SUCCESS:" + gson.toJson(allItems));
        System.out.println("[HANDLER] Admin " + loggedInUsername
                + " lấy toàn bộ " + allItems.size() + " sản phẩm.");
    }

    // =========================================================================
    // HANDLER: WATCH_ITEM
    // =========================================================================
    private void handleWatchItem(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            sendMessage("FAIL:Thiếu itemId. Format: WATCH_ITEM:<itemId>");
            return;
        }
        try {
            int itemId = Integer.parseInt(parts[1].trim());
            watchedItemId = itemId;
            sendMessage("SUCCESS:Đang theo dõi phiên #" + itemId);
            System.out.printf("[HANDLER] %s đăng ký watch item #%d%n", loggedInUsername, itemId);
        } catch (NumberFormatException e) {
            sendMessage("FAIL:itemId phải là số nguyên");
        }
    }

    // =========================================================================
    // HANDLER: GET_ALL_USERS (Admin only)
    // =========================================================================
    private void handleGetAllUsers() {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (!"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Admin mới có quyền xem danh sách người dùng");
            return;
        }

        List<String[]> users = userDAO.getAllUsers();
        List<java.util.Map<String, String>> result = new java.util.ArrayList<>();
        for (String[] u : users) {
            java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
            map.put("id",         u[0]);
            map.put("username",   u[1]);
            map.put("email",      u[2]);
            map.put("role",       u[3]);
            map.put("created_at", u[4]);
            map.put("balance",    u[5]);
            result.add(map);
        }
        sendMessage("SUCCESS:" + gson.toJson(result));
        System.out.println("[HANDLER] Admin " + loggedInUsername
                + " lấy danh sách " + users.size() + " users.");
    }

    // =========================================================================
    // HANDLER: DELETE_USER (Admin only)
    // =========================================================================
    private void handleDeleteUser(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (!"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Admin mới có quyền xóa người dùng");
            return;
        }
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            sendMessage("FAIL:Thiếu username. Format: DELETE_USER:<username>");
            return;
        }

        String targetUsername = parts[1].trim();
        boolean ok = userDAO.deleteUser(targetUsername, loggedInUsername);
        if (ok) {
            sendMessage("SUCCESS:Đã xóa tài khoản '" + targetUsername + "'");
        } else {
            sendMessage("FAIL:Không thể xóa. User không tồn tại, "
                    + "là ADMIN, hoặc bạn đang tự xóa chính mình.");
        }
    }

    // =========================================================================
    // HANDLER: UPDATE_USER_ROLE (Admin only)
    // =========================================================================
    private void handleUpdateUserRole(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (!"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Admin mới có quyền thay đổi role người dùng");
            return;
        }
        if (parts.length < 3 || parts[1].trim().isEmpty() || parts[2].trim().isEmpty()) {
            sendMessage("FAIL:Format: UPDATE_USER_ROLE:<username>:<newRole>");
            return;
        }

        String targetUsername = parts[1].trim();
        String newRole        = parts[2].trim().toUpperCase();

        boolean ok = userDAO.updateUserRole(targetUsername, newRole, loggedInUsername);
        if (ok) {
            sendMessage("SUCCESS:Đã đổi role của '" + targetUsername + "' → " + newRole);
        } else {
            sendMessage("FAIL:Không thể đổi role. Kiểm tra: username tồn tại, "
                    + "role hợp lệ (BIDDER/SELLER), không đổi role ADMIN.");
        }
    }

    // =========================================================================
    // HANDLER: CHANGE_ITEM_STATUS (Admin only)
    // =========================================================================
    private void handleChangeItemStatus(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (!"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Admin mới có quyền thay đổi trạng thái phiên");
            return;
        }
        if (parts.length < 3 || parts[1].trim().isEmpty() || parts[2].trim().isEmpty()) {
            sendMessage("FAIL:Format: CHANGE_ITEM_STATUS:<itemId>:<status>");
            return;
        }

        try {
            int    itemId    = Integer.parseInt(parts[1].trim());
            String statusStr = parts[2].trim().toUpperCase();

            if (!statusStr.equals("PAID") && !statusStr.equals("CANCELED")) {
                sendMessage("FAIL:Status không hợp lệ. Admin chỉ được đặt: PAID, CANCELED");
                return;
            }

            Item.Status newStatus = Item.Status.valueOf(statusStr);
            itemDAO.updateStatus(itemId, newStatus);
            sendMessage("SUCCESS:Đã cập nhật phiên #" + itemId + " → " + statusStr);
            System.out.printf("[HANDLER] Admin %s đổi status item #%d → %s%n",
                    loggedInUsername, itemId, statusStr);

        } catch (NumberFormatException e) {
            sendMessage("FAIL:itemId phải là số nguyên");
        } catch (IllegalArgumentException e) {
            sendMessage("FAIL:Status không hợp lệ: " + parts[2].trim());
        }
    }

    // =========================================================================
    // HANDLER: PLACE_BID
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
            int itemId       = Integer.parseInt(parts[1].trim());
            double bidAmount = Double.parseDouble(parts[2].trim());
            processPlaceBid(itemId, bidAmount);
        } catch (NumberFormatException e) {
            sendMessage("FAIL:itemId hoặc bidAmount không hợp lệ");
        }
    }

    private void processPlaceBid(int itemId, double bidAmount) {
        String result = bidDAO.placeBidTransaction(itemId, loggedInUsername, bidAmount);

        if ("SUCCESS".equals(result)) {
            try {
                auctionService.handlePostBidSuccess(itemId, loggedInUsername, bidAmount, server);
            } catch (Exception e) {
                System.err.println("[HANDLER] Lỗi broadcast sau bid: " + e.getMessage());
            }

            sendMessage("SUCCESS:Đặt giá thành công $" + bidAmount);
            System.out.println("[HANDLER] " + loggedInUsername
                    + " đặt $" + bidAmount + " cho item #" + itemId);
        } else {
            sendMessage("FAIL:" + result.replace("ERROR: ", ""));
            System.err.println("[HANDLER] Đặt giá thất bại: " + result);
        }
    }

    // =========================================================================
    // GỬI TIN NHẮN VỀ CLIENT
    // =========================================================================
    public synchronized void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    // =========================================================================
    // HANDLER: GET_BID_HISTORY
    // =========================================================================
    private void handleGetBidHistory(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
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
    // HANDLER: ADD_ITEM
    //
    // Nhận raw string thay vì parts[] vì payload JSON có thể chứa dấu ':'
    // và Base64 ảnh rất dài (>6 phần khi split).
    //
    // Format: ADD_ITEM:<jsonPayload>
    // JSON hỗ trợ thêm 2 field mới (tùy chọn):
    //   "imageBase64" : chuỗi Base64 của file ảnh
    //   "imageExt"    : phần mở rộng file ("jpg", "png", "gif")
    // =========================================================================
    private void handleAddItem(String raw) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (!"SELLER".equals(loggedInRole) && !"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Seller hoặc Admin mới được thêm sản phẩm");
            return;
        }

        // Lấy JSON sau "ADD_ITEM:" — dùng indexOf để không bị giới hạn bởi split limit
        int colonIdx = raw.indexOf(':');
        if (colonIdx < 0 || colonIdx == raw.length() - 1) {
            sendMessage("FAIL:Format: ADD_ITEM:{\"name\":\"...\",\"startingPrice\":100.0,\"endTime\":1748000000000,\"category\":\"ELECTRONICS\"}");
            return;
        }
        String jsonPayload = raw.substring(colonIdx + 1).trim();

        try {
            AddItemPayload p = gson.fromJson(jsonPayload, AddItemPayload.class);

            // Validate các trường bắt buộc
            if (p.name == null || p.name.trim().isEmpty()) {
                sendMessage("FAIL:Tên sản phẩm không được để trống");
                return;
            }
            if (p.startingPrice <= 0) {
                sendMessage("FAIL:Giá khởi điểm phải lớn hơn 0");
                return;
            }
            if (p.endTime <= System.currentTimeMillis()) {
                sendMessage("FAIL:Thời gian kết thúc phải ở tương lai");
                return;
            }

            // Validate startTime (nếu có): phải ở tương lai và trước endTime
            if (p.startTime > 0) {
                if (p.startTime <= System.currentTimeMillis()) {
                    sendMessage("FAIL:Thời gian bắt đầu phải ở tương lai");
                    return;
                }
                if (p.startTime >= p.endTime) {
                    sendMessage("FAIL:Thời gian bắt đầu phải trước thời gian kết thúc");
                    return;
                }
            }

            String category    = (p.category != null && !p.category.trim().isEmpty())
                    ? p.category.trim().toUpperCase() : "ELECTRONICS";
            String description = (p.description != null) ? p.description.trim() : "";
            int    sellerId    = userDAO.getUserIdByUsername(loggedInUsername);

            // Factory Method: tạo đúng subclass (Electronics / Art / Vehicle)
            // Dùng overload 7 tham số khi có startTime, overload 6 tham số khi bắt đầu ngay
            Item newItem;
            if (p.startTime > 0) {
                newItem = ItemFactory.create(category, p.name.trim(), description,
                        p.startingPrice, p.startTime, p.endTime, sellerId);
            } else {
                newItem = ItemFactory.create(category, p.name.trim(), description,
                        p.startingPrice, p.endTime, sellerId);
            }

            // ── XỬ LÝ ẢNH BASE64 (MỚI) ───────────────────────────────────────
            // Nếu client gửi kèm ảnh thì giải mã và lưu vào thư mục uploads/.
            // Nếu không có ảnh (imageBase64 == null) thì bỏ qua — hoàn toàn tương thích ngược.
            if (p.imageBase64 != null && !p.imageBase64.trim().isEmpty()) {
                try {
                    // Giải mã Base64 → bytes
                    byte[] imageBytes = Base64.getDecoder().decode(p.imageBase64.trim());

                    // Chỉ cho phép jpg, png, gif; mặc định jpg nếu không rõ
                    String ext = (p.imageExt != null && p.imageExt.trim().matches("jpg|png|gif"))
                            ? p.imageExt.trim() : "jpg";

                    // Tạo thư mục uploads/ nếu chưa tồn tại
                    Path uploadsDir = Paths.get("uploads");
                    if (!Files.exists(uploadsDir)) {
                        Files.createDirectories(uploadsDir);
                    }

                    // Đặt tên file bằng UUID để tránh trùng
                    String fileName = UUID.randomUUID().toString() + "." + ext;
                    Path   filePath = uploadsDir.resolve(fileName);
                    Files.write(filePath, imageBytes);

                    newItem.setImagePath("uploads/" + fileName);
                    System.out.println("[HANDLER] Đã lưu ảnh: " + filePath.toAbsolutePath());

                } catch (IllegalArgumentException e) {
                    // Base64 không hợp lệ → bỏ qua ảnh, vẫn tạo item bình thường
                    System.err.println("[HANDLER] Base64 ảnh không hợp lệ, bỏ qua: " + e.getMessage());
                } catch (IOException e) {
                    // Lỗi ghi file → bỏ qua ảnh, vẫn tạo item bình thường
                    System.err.println("[HANDLER] Không thể lưu file ảnh, bỏ qua: " + e.getMessage());
                }
            }
            // ─────────────────────────────────────────────────────────────────

            int newId = itemDAO.addItem(newItem);

            if (newId > 0) {
                sendMessage("SUCCESS:Đã thêm sản phẩm #" + newId);
                System.out.println("[HANDLER] " + loggedInUsername + " thêm item #" + newId
                        + " [" + category + "]: " + p.name.trim()
                        + (newItem.getImagePath() != null ? " (có ảnh)" : ""));
            } else {
                sendMessage("FAIL:Không thể thêm sản phẩm. Kiểm tra lại dữ liệu.");
            }

        } catch (com.google.gson.JsonSyntaxException e) {
            sendMessage("FAIL:JSON không hợp lệ. Format: ADD_ITEM:{\"name\":\"...\","
                    + "\"startingPrice\":100.0,\"endTime\":1748000000000,\"category\":\"ELECTRONICS\"}");
        }
    }

    // =========================================================================
    // HANDLER: UPDATE_ITEM
    // =========================================================================
    private void handleUpdateItem(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (!"SELLER".equals(loggedInRole) && !"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Seller hoặc Admin mới được sửa sản phẩm");
            return;
        }
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            sendMessage("FAIL:Format: UPDATE_ITEM:{\"itemId\":5,\"name\":\"...\",\"description\":\"...\","
                    + "\"startingPrice\":150.0,\"endTime\":1748000000000}");
            return;
        }

        String jsonPayload = String.join(":", java.util.Arrays.copyOfRange(parts, 1, parts.length)).trim();

        try {
            UpdateItemPayload p = gson.fromJson(jsonPayload, UpdateItemPayload.class);
            if (p.itemId <= 0) {
                sendMessage("FAIL:itemId không hợp lệ");
                return;
            }
            if (p.name == null || p.name.trim().isEmpty()) {
                sendMessage("FAIL:Tên sản phẩm không được để trống");
                return;
            }
            if (p.startingPrice <= 0) {
                sendMessage("FAIL:Giá khởi điểm phải lớn hơn 0");
                return;
            }
            if (p.endTime <= System.currentTimeMillis()) {
                sendMessage("FAIL:Thời gian kết thúc phải ở tương lai");
                return;
            }

            if ("SELLER".equals(loggedInRole)) {
                Item target = itemDAO.getItemById(p.itemId);
                if (target == null) {
                    sendMessage("FAIL:Sản phẩm #" + p.itemId + " không tồn tại.");
                    return;
                }
                int myId = userDAO.getUserIdByUsername(loggedInUsername);
                if (target.getSellerId() != myId) {
                    sendMessage("FAIL:Bạn không có quyền sửa sản phẩm của người khác.");
                    return;
                }
            }

            String description = (p.description != null) ? p.description.trim() : "";
            boolean ok = itemDAO.updateItem(p.itemId, p.name.trim(), description,
                    p.startingPrice, p.endTime);
            if (ok) {
                sendMessage("SUCCESS:Đã cập nhật sản phẩm #" + p.itemId);
                System.out.printf("[HANDLER] %s cập nhật item #%d: %s%n",
                        loggedInUsername, p.itemId, p.name.trim());
            } else {
                sendMessage("FAIL:Không thể sửa. Phiên có thể đã RUNNING hoặc không tồn tại.");
            }
        } catch (com.google.gson.JsonSyntaxException e) {
            sendMessage("FAIL:JSON không hợp lệ. Format: UPDATE_ITEM:{\"itemId\":5,\"name\":\"...\","
                    + "\"description\":\"...\",\"startingPrice\":150.0,\"endTime\":1748000000000}");
        }
    }

    // =========================================================================
    // HANDLER: DELETE_ITEM
    // =========================================================================
    private void handleDeleteItem(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (!"SELLER".equals(loggedInRole) && !"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Seller hoặc Admin mới được xóa sản phẩm");
            return;
        }
        if (parts.length < 2) {
            sendMessage("FAIL:Thiếu itemId");
            return;
        }
        try {
            int itemId = Integer.parseInt(parts[1].trim());

            // ── ADMIN: force-delete bất kể trạng thái ──────────────────────
            if ("ADMIN".equals(loggedInRole)) {
                Item target = itemDAO.getItemById(itemId);
                if (target == null) {
                    sendMessage("FAIL:Sản phẩm #" + itemId + " không tồn tại.");
                    return;
                }
                // Nếu phiên đang RUNNING → thông báo cho tất cả client đang xem
                if (target.getStatus() == Item.Status.RUNNING) {
                    server.broadcastToItemWatchers(itemId,
                            new Message("AUCTION_ENDED",
                                    "Phiên đấu giá '" + target.getName()
                                            + "' đã bị Admin xóa khỏi hệ thống."));
                }
                boolean ok = itemDAO.adminForceDeleteItem(itemId);
                if (ok) {
                    sendMessage("SUCCESS:Đã xóa sản phẩm #" + itemId
                            + " (" + target.getName() + ")");
                    System.out.println("[HANDLER] Admin '" + loggedInUsername
                            + "' đã force-delete item #" + itemId
                            + " (trạng thái: " + target.getStatus() + ")");
                } else {
                    sendMessage("FAIL:Không thể xóa sản phẩm #" + itemId);
                }
                return;
            }

            // ── SELLER: chỉ được xóa sản phẩm của mình, không xóa RUNNING ──
            Item target = itemDAO.getItemById(itemId);
            if (target == null) {
                sendMessage("FAIL:Sản phẩm #" + itemId + " không tồn tại.");
                return;
            }
            int myId = userDAO.getUserIdByUsername(loggedInUsername);
            if (target.getSellerId() != myId) {
                sendMessage("FAIL:Bạn không có quyền xóa sản phẩm của người khác.");
                return;
            }

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
    // HANDLER: ADMIN_DELETE_FINISHED — xóa hàng loạt sản phẩm đã kết thúc
    // =========================================================================
    private void handleAdminDeleteFinished() {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (!"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Admin mới có quyền xóa hàng loạt");
            return;
        }
        int count = itemDAO.deleteAllFinishedItems();
        if (count >= 0) {
            sendMessage("SUCCESS:Đã xóa " + count + " sản phẩm đã kết thúc (FINISHED/PAID/CANCELED).");
            System.out.println("[HANDLER] Admin '" + loggedInUsername
                    + "' batch-deleted " + count + " finished items.");
        } else {
            sendMessage("FAIL:Lỗi khi xóa hàng loạt. Kiểm tra log server.");
        }
    }

    // =========================================================================
    // HANDLER: GET_MY_ITEMS
    // =========================================================================
    private void handleGetMyItems() {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (!"SELLER".equals(loggedInRole) && !"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Seller hoặc Admin mới có danh sách sản phẩm đăng bán");
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
    // HANDLER: GET_WON_ITEMS — Giỏ hàng của Bidder
    // Trả về các sản phẩm mà bidder đã đấu giá thắng (FINISHED / PAID)
    // =========================================================================
    private void handleGetWonItems() {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (!"BIDDER".equals(loggedInRole) && !"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Bidder mới có giỏ hàng đấu giá");
            return;
        }
        List<Item> wonItems = itemDAO.getWonItems(loggedInUsername);
        System.out.printf("[HANDLER] %s yêu cầu giỏ hàng → %d sản phẩm thắng%n",
                loggedInUsername, wonItems.size());
        sendMessage("SUCCESS:" + gson.toJson(wonItems));
    }

    // =========================================================================
    // HANDLER: AUTO_BID
    // =========================================================================
    private void handleAutoBid(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (parts.length < 4) {
            sendMessage("FAIL:Format: AUTO_BID:<itemId>:<maxBid>:<increment>");
            return;
        }

        int    itemId;
        double maxBid, increment;
        try {
            itemId    = Integer.parseInt(parts[1].trim());
            maxBid    = Double.parseDouble(parts[2].trim());
            increment = Double.parseDouble(parts[3].trim());
        } catch (NumberFormatException e) {
            sendMessage("FAIL:itemId, maxBid và increment phải là số hợp lệ");
            return;
        }

        if (maxBid <= 0 || increment <= 0) {
            sendMessage("FAIL:maxBid và increment phải lớn hơn 0");
            return;
        }

        Item item = itemDAO.getItemById(itemId);
        if (item == null) {
            sendMessage("FAIL:Không tìm thấy sản phẩm #" + itemId);
            return;
        }
        if (item.getStatus() != Item.Status.RUNNING) {
            sendMessage("FAIL:Phiên đấu giá không còn đang chạy (trạng thái: " + item.getStatus() + ")");
            return;
        }
        if (maxBid <= item.getCurrentHighestBid()) {
            sendMessage(String.format("FAIL:maxBid ($%.2f) phải cao hơn giá hiện tại ($%.2f)",
                    maxBid, item.getCurrentHighestBid()));
            return;
        }

        auctionService.registerAutoBid(itemId, loggedInUsername, maxBid, increment);

        sendMessage("SUCCESS:Đã đăng ký auto-bid thành công!");

        final String currentBidder = item.getCurrentHighestBidder();
        final double currentBid    = item.getCurrentHighestBid();
        CompletableFuture.runAsync(() ->
                        auctionService.triggerAutoBids(itemId, currentBidder, currentBid, server))
                .exceptionally(ex -> {
                    System.err.println("[HANDLER] Lỗi async triggerAutoBids item #"
                            + itemId + ": " + ex.getMessage());
                    return null;
                });
    }

    // =========================================================================
    // HANDLER: CANCEL_AUTO_BID
    // =========================================================================
    private void handleCancelAutoBid(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (parts.length < 2) {
            sendMessage("FAIL:Format: CANCEL_AUTO_BID:<itemId>");
            return;
        }

        try {
            int itemId = Integer.parseInt(parts[1].trim());
            auctionService.cancelAutoBid(itemId, loggedInUsername);
            sendMessage("SUCCESS:Đã hủy auto-bid cho phiên #" + itemId);
        } catch (NumberFormatException e) {
            sendMessage("FAIL:itemId không hợp lệ");
        }
    }

    // =========================================================================
    // HANDLER: GET_BALANCE — lấy số dư ví của người dùng đang đăng nhập
    // =========================================================================
    private void handleGetBalance() {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        double balance = userDAO.getBalance(loggedInUsername);
        if (balance < 0) {
            sendMessage("FAIL:Không thể lấy số dư. Kiểm tra kết nối database.");
        } else {
            sendMessage(String.format("SUCCESS:%.2f", balance));
        }
    }

    // =========================================================================
    // HANDLER: DEPOSIT — nạp tiền vào ví
    //
    // Format: DEPOSIT:<amount>
    // Giới hạn mỗi lần nạp: $1 – $100,000
    // =========================================================================
    private void handleDeposit(String[] parts) {
        if (loggedInUsername == null) {
            sendMessage("FAIL:Bạn chưa đăng nhập");
            return;
        }
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            sendMessage("FAIL:Thiếu số tiền. Format: DEPOSIT:<amount>");
            return;
        }
        try {
            double amount = Double.parseDouble(parts[1].trim());
            if (amount <= 0) {
                sendMessage("FAIL:Số tiền nạp phải lớn hơn 0");
                return;
            }
            if (amount > 100_000) {
                sendMessage("FAIL:Mỗi lần nạp tối đa $100,000");
                return;
            }
            double newBalance = userDAO.deposit(loggedInUsername, amount);
            if (newBalance < 0) {
                sendMessage("FAIL:Nạp tiền thất bại. Thử lại sau.");
            } else {
                sendMessage(String.format("SUCCESS:%.2f", newBalance));
                System.out.println("[HANDLER] '" + loggedInUsername
                        + "' nạp $" + amount + " → số dư: $" + newBalance);
            }
        } catch (NumberFormatException e) {
            sendMessage("FAIL:Số tiền không hợp lệ");
        }
    }

    // =========================================================================
    // HANDLER: DEPOSIT_REQUEST — Bidder/Seller gửi yêu cầu nạp tiền cho Admin
    // Format: DEPOSIT_REQUEST:<amount>
    // =========================================================================
    private void handleDepositRequest(String[] parts) {
        if (loggedInUsername == null) { sendMessage("FAIL:Bạn chưa đăng nhập"); return; }
        if ("ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Admin hãy dùng nút nạp trực tiếp trong ví"); return;
        }
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            sendMessage("FAIL:Thiếu số tiền. Format: DEPOSIT_REQUEST:<amount>"); return;
        }
        try {
            double amount = Double.parseDouble(parts[1].trim());
            if (amount <= 0)       { sendMessage("FAIL:Số tiền phải lớn hơn 0"); return; }
            if (amount > 100_000)  { sendMessage("FAIL:Mỗi yêu cầu tối đa $100,000"); return; }
            int id = auctionService.addDepositRequest(loggedInUsername, amount);
            sendMessage("SUCCESS:Yêu cầu nạp $" + String.format("%.2f", amount)
                    + " đã gửi đến Admin. Mã yêu cầu: #" + id);
        } catch (NumberFormatException e) {
            sendMessage("FAIL:Số tiền không hợp lệ");
        }
    }

    // =========================================================================
    // HANDLER: ADMIN_DEPOSIT — Admin nạp tiền trực tiếp vào ví của chính mình
    // Format: ADMIN_DEPOSIT:<amount>
    // =========================================================================
    private void handleAdminDeposit(String[] parts) {
        if (loggedInUsername == null) { sendMessage("FAIL:Bạn chưa đăng nhập"); return; }
        if (!"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Admin mới được dùng lệnh này"); return;
        }
        if (parts.length < 2) { sendMessage("FAIL:Thiếu số tiền"); return; }
        try {
            double amount = Double.parseDouble(parts[1].trim());
            if (amount <= 0)      { sendMessage("FAIL:Số tiền phải lớn hơn 0"); return; }
            if (amount > 100_000) { sendMessage("FAIL:Mỗi lần nạp tối đa $100,000"); return; }
            double newBalance = userDAO.deposit(loggedInUsername, amount);
            if (newBalance < 0) { sendMessage("FAIL:Nạp tiền thất bại"); return; }
            sendMessage(String.format("SUCCESS:%.2f", newBalance));
            System.out.printf("[HANDLER] Admin '%s' tự nạp $%.2f → số dư: $%.2f%n",
                    loggedInUsername, amount, newBalance);
        } catch (NumberFormatException e) {
            sendMessage("FAIL:Số tiền không hợp lệ");
        }
    }

    // =========================================================================
    // HANDLER: GET_DEPOSIT_REQUESTS — Admin lấy danh sách yêu cầu nạp tiền
    // =========================================================================
    private void handleGetDepositRequests() {
        if (loggedInUsername == null) { sendMessage("FAIL:Bạn chưa đăng nhập"); return; }
        if (!"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Admin mới xem được danh sách yêu cầu"); return;
        }
        sendMessage("SUCCESS:" + gson.toJson(auctionService.getAllDepositRequests()));
    }

    // =========================================================================
    // HANDLER: APPROVE_DEPOSIT — Admin duyệt yêu cầu nạp tiền
    // Format: APPROVE_DEPOSIT:<requestId>
    // =========================================================================
    private void handleApproveDeposit(String[] parts) {
        if (loggedInUsername == null) { sendMessage("FAIL:Bạn chưa đăng nhập"); return; }
        if (!"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Admin mới duyệt được yêu cầu"); return;
        }
        if (parts.length < 2) { sendMessage("FAIL:Thiếu request ID"); return; }
        try {
            int reqId = Integer.parseInt(parts[1].trim());
            com.auction.server.services.AuctionService.DepositRequest req =
                    auctionService.getDepositRequest(reqId);
            if (req == null) { sendMessage("FAIL:Không tìm thấy yêu cầu #" + reqId); return; }
            if (!"PENDING".equals(req.status)) {
                sendMessage("FAIL:Yêu cầu #" + reqId + " đã được xử lý (" + req.status + ")"); return;
            }
            double newBalance = userDAO.deposit(req.username, req.amount);
            if (newBalance < 0) { sendMessage("FAIL:Nạp tiền thất bại cho " + req.username); return; }
            auctionService.approveDepositRequest(reqId);
            sendMessage(String.format("SUCCESS:Đã duyệt! Nạp $%.2f cho %s. Số dư mới: $%.2f",
                    req.amount, req.username, newBalance));
            System.out.printf("[HANDLER] Admin duyệt yêu cầu #%d: +$%.2f cho %s%n",
                    reqId, req.amount, req.username);
        } catch (NumberFormatException e) {
            sendMessage("FAIL:ID không hợp lệ");
        }
    }

    // =========================================================================
    // HANDLER: REJECT_DEPOSIT — Admin từ chối yêu cầu nạp tiền
    // Format: REJECT_DEPOSIT:<requestId>
    // =========================================================================
    private void handleRejectDeposit(String[] parts) {
        if (loggedInUsername == null) { sendMessage("FAIL:Bạn chưa đăng nhập"); return; }
        if (!"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Admin mới từ chối được yêu cầu"); return;
        }
        if (parts.length < 2) { sendMessage("FAIL:Thiếu request ID"); return; }
        try {
            int reqId = Integer.parseInt(parts[1].trim());
            com.auction.server.services.AuctionService.DepositRequest req =
                    auctionService.getDepositRequest(reqId);
            if (req == null) { sendMessage("FAIL:Không tìm thấy yêu cầu #" + reqId); return; }
            if (!"PENDING".equals(req.status)) {
                sendMessage("FAIL:Yêu cầu #" + reqId + " đã được xử lý (" + req.status + ")"); return;
            }
            auctionService.rejectDepositRequest(reqId);
            sendMessage("SUCCESS:Đã từ chối yêu cầu #" + reqId + " của " + req.username);
            System.out.printf("[HANDLER] Admin từ chối yêu cầu #%d của %s%n", reqId, req.username);
        } catch (NumberFormatException e) {
            sendMessage("FAIL:ID không hợp lệ");
        }
    }

    // =========================================================================
    // HANDLER: ADJUST_BALANCE — Admin điều chỉnh số dư của bất kỳ user nào
    // Format: ADJUST_BALANCE:<username>:<newBalance>
    // =========================================================================
    private void handleAdjustBalance(String[] parts) {
        if (loggedInUsername == null) { sendMessage("FAIL:Bạn chưa đăng nhập"); return; }
        if (!"ADMIN".equals(loggedInRole)) {
            sendMessage("FAIL:Chỉ Admin mới điều chỉnh được số dư"); return;
        }
        if (parts.length < 3) {
            sendMessage("FAIL:Format: ADJUST_BALANCE:<username>:<newBalance>"); return;
        }
        try {
            String targetUser = parts[1].trim();
            double newBalance = Double.parseDouble(parts[2].trim());
            if (newBalance < 0) { sendMessage("FAIL:Số dư không được âm"); return; }
            boolean ok = userDAO.setBalance(targetUser, newBalance);
            if (!ok) { sendMessage("FAIL:Không tìm thấy user '" + targetUser + "'"); return; }
            sendMessage(String.format("SUCCESS:Đã đặt số dư của %s = $%.2f", targetUser, newBalance));
            System.out.printf("[HANDLER] Admin đặt số dư %s = $%.2f%n", targetUser, newBalance);
        } catch (NumberFormatException e) {
            sendMessage("FAIL:Số tiền không hợp lệ");
        }
    }

    private void closeConnections() {
        watchedItemId = -1;
        try {
            if (server != null) server.removeClient(this);
            if (in     != null) in.close();
            if (out    != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // INNER PAYLOAD CLASSES — dùng cho Gson deserialize JSON từ client
    // =========================================================================

    /** Payload cho ADD_ITEM */
    private static class AddItemPayload {
        String name;
        String description;
        double startingPrice;
        long   startTime;     // epoch ms; 0 hoặc không gửi = bắt đầu ngay khi đăng
        long   endTime;
        String category;      // ELECTRONICS | ART | VEHICLE (tuỳ chọn, mặc định ELECTRONICS)
        String imageBase64;   // ảnh encode Base64 (null nếu không có ảnh)
        String imageExt;      // phần mở rộng file: "jpg", "png", "gif"
    }

    /** Payload cho UPDATE_ITEM */
    private static class UpdateItemPayload {
        int    itemId;
        String name;
        String description;
        double startingPrice;
        long   endTime;
    }

    private static class PlaceBidPayload {
        int    itemId;
        double bidAmount;
    }
}