package com.auction.client.controllers;

import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;

/**
 * Controller màn hình Admin Panel.
 *
 * Chức năng:
 *   Tab 1 — Quản lý Người dùng   : xem danh sách, đổi role, xóa tài khoản.
 *   Tab 2 — Quản lý Sản phẩm    : xem tất cả item, đổi trạng thái, xóa.
 *   Tab 3 — Yêu cầu Nạp tiền    : duyệt/từ chối deposit request,
 *                                   điều chỉnh số dư thủ công cho bất kỳ user nào.
 *
 * Giao tiếp với server:
 *   GET_ALL_USERS, DELETE_USER, UPDATE_USER_ROLE,
 *   GET_ALL_ITEMS, DELETE_ITEM, CHANGE_ITEM_STATUS,
 *   GET_DEPOSIT_REQUESTS, APPROVE_DEPOSIT, REJECT_DEPOSIT, ADJUST_BALANCE
 */
public class AdminDashboardController implements Initializable {

    // ── Tab 1: Users ────────────────────────────────────────────────
    @FXML public TableView<ObservableList<String>>  tblUsers;
    @FXML public TableColumn<ObservableList<String>, String> colUserId;
    @FXML public TableColumn<ObservableList<String>, String> colUsername;
    @FXML public TableColumn<ObservableList<String>, String> colEmail;
    @FXML public TableColumn<ObservableList<String>, String> colRole;
    @FXML public TableColumn<ObservableList<String>, String> colBalance;
    @FXML public TableColumn<ObservableList<String>, String> colUserCreated;
    @FXML public ComboBox<String> cmbNewRole;
    @FXML public Label lblUserMsg;
    @FXML public Label lblAdminName;

    // ── Tab 2: Items ────────────────────────────────────────────────
    @FXML public TableView<ObservableList<String>>  tblItems;
    @FXML public TableColumn<ObservableList<String>, String> colItemId;
    @FXML public TableColumn<ObservableList<String>, String> colItemName;
    @FXML public TableColumn<ObservableList<String>, String> colItemCategory;
    @FXML public TableColumn<ObservableList<String>, String> colItemPrice;
    @FXML public TableColumn<ObservableList<String>, String> colItemStatus;
    @FXML public TableColumn<ObservableList<String>, String> colSellerId;
    @FXML public ComboBox<String> cmbItemStatus;
    @FXML public Label lblItemMsg;

    // ── Tab 3: Deposit Requests ──────────────────────────────────────
    @FXML public TableView<ObservableList<String>>  tblDepositRequests;
    @FXML public TableColumn<ObservableList<String>, String> colReqId;
    @FXML public TableColumn<ObservableList<String>, String> colReqUsername;
    @FXML public TableColumn<ObservableList<String>, String> colReqAmount;
    @FXML public TableColumn<ObservableList<String>, String> colReqStatus;
    @FXML public TableColumn<ObservableList<String>, String> colReqTime;
    @FXML public Label lblPendingBadge;
    @FXML public Label lblSelectedReq;
    @FXML public Label lblDepositMsg;

    // ── Tab 3: Adjust Balance ────────────────────────────────────────
    @FXML public TextField txtAdjustUsername;
    @FXML public TextField txtAdjustAmount;
    @FXML public Label lblAdjustMsg;

    private final NavigationUtils navUtils = new NavigationUtils();
    private final Gson gson = new Gson();
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    // =========================================================================
    // KHỞI TẠO
    // =========================================================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (lblAdminName != null && ClientService.currentUsername != null) {
            lblAdminName.setText("Logged in: " + ClientService.currentUsername);
        }

        if (cmbNewRole != null) {
            cmbNewRole.getItems().addAll("BIDDER", "SELLER", "ADMIN");
            cmbNewRole.setValue("BIDDER");
        }

        if (cmbItemStatus != null) {
            cmbItemStatus.getItems().addAll("OPEN", "RUNNING", "FINISHED", "PAID", "CANCELED");
            cmbItemStatus.setValue("OPEN");
        }

        // Tab 1 — Users: [id, username, email, role, balance, created_at]
        bindColumn(colUserId,      0);
        bindColumn(colUsername,    1);
        bindColumn(colEmail,       2);
        bindColumn(colRole,        3);
        bindColumn(colBalance,     4);
        bindColumn(colUserCreated, 5);

        // Tab 2 — Items
        bindColumn(colItemId,       0);
        bindColumn(colItemName,     1);
        bindColumn(colItemCategory, 2);
        bindColumn(colItemPrice,    3);
        bindColumn(colItemStatus,   4);
        bindColumn(colSellerId,     5);

        // Tab 3 — Deposit Requests: [id, username, amount, status, time]
        bindColumn(colReqId,       0);
        bindColumn(colReqUsername, 1);
        bindColumn(colReqAmount,   2);
        bindColumn(colReqStatus,   3);
        bindColumn(colReqTime,     4);

        // Khi chọn dòng trong bảng yêu cầu → hiển thị thông tin
        if (tblDepositRequests != null) {
            tblDepositRequests.getSelectionModel().selectedItemProperty()
                    .addListener((obs, oldVal, newVal) -> {
                        if (newVal != null && newVal.size() >= 4) {
                            String info = String.format("YC #%s — %s yêu cầu nạp %s [%s]",
                                    newVal.get(0), newVal.get(1),
                                    newVal.get(2), newVal.get(3));
                            if (lblSelectedReq != null) lblSelectedReq.setText(info);
                        }
                    });
        }

        // Tải dữ liệu ngay khi mở
        handleLoadUsers();
        handleLoadItems();
        handleLoadDepositRequests();
    }

    // =========================================================================
    // TAB 1: QUẢN LÝ NGƯỜI DÙNG
    // =========================================================================

    @FXML
    public void handleLoadUsers() {
        runInBackground("GET_ALL_USERS", response -> {
            if (response == null || !response.startsWith("SUCCESS:")) {
                setUserMsg("❌ Không thể tải danh sách người dùng.", false);
                return;
            }
            String json = response.substring("SUCCESS:".length());
            ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();

            try {
                JsonArray arr = gson.fromJson(json, JsonArray.class);
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    ObservableList<String> row = FXCollections.observableArrayList(
                            safeGet(obj, "id"),
                            safeGet(obj, "username"),
                            safeGet(obj, "email"),
                            safeGet(obj, "role"),
                            "$" + safeGet(obj, "balance"),
                            safeGet(obj, "created_at")
                    );
                    data.add(row);
                }
                Platform.runLater(() -> {
                    tblUsers.setItems(data);
                    setUserMsg("✅ Đã tải " + data.size() + " tài khoản.", true);
                });
            } catch (Exception e) {
                setUserMsg("❌ Lỗi parse dữ liệu: " + e.getMessage(), false);
            }
        });
    }

    @FXML
    public void handleChangeRole() {
        ObservableList<String> selected = tblUsers.getSelectionModel().getSelectedItem();
        if (selected == null || selected.size() < 2) {
            setUserMsg("⚠ Vui lòng chọn một người dùng từ bảng.", false);
            return;
        }
        String username = selected.get(1);
        String newRole  = cmbNewRole.getValue();
        if (newRole == null) {
            setUserMsg("⚠ Vui lòng chọn role mới.", false);
            return;
        }
        if (username.equals(ClientService.currentUsername)) {
            setUserMsg("⚠ Không thể tự đổi role của chính mình.", false);
            return;
        }

        runInBackground("UPDATE_USER_ROLE:" + username + ":" + newRole, response -> {
            if (response != null && response.startsWith("SUCCESS")) {
                setUserMsg("✅ Đã đổi role của '" + username + "' thành " + newRole + ".", true);
                handleLoadUsers();
            } else {
                String reason = parseFailReason(response);
                setUserMsg("❌ " + reason, false);
            }
        });
    }

    @FXML
    public void handleDeleteUser() {
        ObservableList<String> selected = tblUsers.getSelectionModel().getSelectedItem();
        if (selected == null || selected.size() < 2) {
            setUserMsg("⚠ Vui lòng chọn một người dùng từ bảng.", false);
            return;
        }
        String username = selected.get(1);
        if (username.equals(ClientService.currentUsername)) {
            setUserMsg("⚠ Không thể xóa tài khoản của chính mình.", false);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận xóa");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn xóa tài khoản '" + username + "'?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                runInBackground("DELETE_USER:" + username, response -> {
                    if (response != null && response.startsWith("SUCCESS")) {
                        setUserMsg("✅ Đã xóa tài khoản '" + username + "'.", true);
                        handleLoadUsers();
                    } else {
                        setUserMsg("❌ " + parseFailReason(response), false);
                    }
                });
            }
        });
    }

    // =========================================================================
    // TAB 2: QUẢN LÝ SẢN PHẨM
    // =========================================================================

    @FXML
    public void handleLoadItems() {
        runInBackground("GET_ALL_ITEMS", response -> {
            if (response == null || !response.startsWith("SUCCESS:")) {
                setItemMsg("❌ Không thể tải danh sách sản phẩm.", false);
                return;
            }
            String json = response.substring("SUCCESS:".length());
            ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();

            try {
                JsonArray arr = gson.fromJson(json, JsonArray.class);
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    ObservableList<String> row = FXCollections.observableArrayList(
                            safeGet(obj, "id"),
                            safeGet(obj, "name"),
                            safeGet(obj, "category"),
                            "$" + safeGet(obj, "currentHighestBid"),
                            safeGet(obj, "status"),
                            safeGet(obj, "sellerId")
                    );
                    data.add(row);
                }
                Platform.runLater(() -> {
                    tblItems.setItems(data);
                    setItemMsg("✅ Đã tải " + data.size() + " sản phẩm.", true);
                });
            } catch (Exception e) {
                setItemMsg("❌ Lỗi parse dữ liệu: " + e.getMessage(), false);
            }
        });
    }

    @FXML
    public void handleChangeItemStatus() {
        ObservableList<String> selected = tblItems.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isEmpty()) {
            setItemMsg("⚠ Vui lòng chọn một sản phẩm từ bảng.", false);
            return;
        }
        String itemId    = selected.get(0);
        String newStatus = cmbItemStatus.getValue();
        if (newStatus == null) {
            setItemMsg("⚠ Vui lòng chọn trạng thái mới.", false);
            return;
        }

        runInBackground("CHANGE_ITEM_STATUS:" + itemId + ":" + newStatus, response -> {
            if (response != null && response.startsWith("SUCCESS")) {
                setItemMsg("✅ Đã đổi trạng thái item #" + itemId + " thành " + newStatus + ".", true);
                handleLoadItems();
            } else {
                setItemMsg("❌ " + parseFailReason(response), false);
            }
        });
    }

    @FXML
    public void handleDeleteItem() {
        ObservableList<String> selected = tblItems.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isEmpty()) {
            setItemMsg("⚠ Vui lòng chọn một sản phẩm từ bảng.", false);
            return;
        }
        String itemId     = selected.get(0);
        String itemName   = selected.size() > 1 ? selected.get(1) : "#" + itemId;
        String itemStatus = selected.size() > 4 ? selected.get(4) : "";

        String warningText = "RUNNING".equals(itemStatus)
                ? "\n\n⚠ Phiên đang RUNNING! Tất cả người đang xem sẽ nhận thông báo hủy."
                : "";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận xóa buộc");
        confirm.setHeaderText("Xóa sản phẩm: " + itemName + " (#" + itemId + ")");
        confirm.setContentText("Trạng thái hiện tại: " + itemStatus
                + "\nThao tác này không thể hoàn tác." + warningText);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                runInBackground("DELETE_ITEM:" + itemId, response -> {
                    if (response != null && response.startsWith("SUCCESS")) {
                        setItemMsg("✅ " + response.substring("SUCCESS:".length()), true);
                        handleLoadItems();
                    } else {
                        setItemMsg("❌ " + parseFailReason(response), false);
                    }
                });
            }
        });
    }

    @FXML
    public void handleDeleteAllFinished() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận xóa hàng loạt");
        confirm.setHeaderText("Xóa tất cả sản phẩm đã kết thúc?");
        confirm.setContentText("Thao tác này sẽ xóa VĨNH VIỄN toàn bộ sản phẩm có trạng thái\n"
                + "FINISHED, PAID và CANCELED khỏi hệ thống.\n\n⚠ Không thể hoàn tác!");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                runInBackground("ADMIN_DELETE_FINISHED", response -> {
                    if (response != null && response.startsWith("SUCCESS")) {
                        setItemMsg("✅ " + response.substring("SUCCESS:".length()), true);
                        handleLoadItems();
                    } else {
                        setItemMsg("❌ " + parseFailReason(response), false);
                    }
                });
            }
        });
    }

    // =========================================================================
    // TAB 3: YÊU CẦU NẠP TIỀN
    // =========================================================================

    /**
     * Tải toàn bộ yêu cầu nạp tiền từ server (mọi trạng thái).
     * Server trả JSON array, mỗi phần tử có: id, username, amount, status, createdAt (ms).
     */
    @FXML
    public void handleLoadDepositRequests() {
        runInBackground("GET_DEPOSIT_REQUESTS", response -> {
            if (response == null || !response.startsWith("SUCCESS:")) {
                setDepositMsg("❌ Không thể tải danh sách yêu cầu.", false);
                return;
            }
            String json = response.substring("SUCCESS:".length());
            ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();
            int pendingCount = 0;

            try {
                JsonArray arr = gson.fromJson(json, JsonArray.class);
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    String status   = safeGet(obj, "status");
                    long   tsMillis = obj.has("createdAt") ? obj.get("createdAt").getAsLong() : 0;
                    String timeStr  = tsMillis > 0 ? sdf.format(new Date(tsMillis)) : "";

                    ObservableList<String> row = FXCollections.observableArrayList(
                            safeGet(obj, "id"),
                            safeGet(obj, "username"),
                            "$" + String.format("%.2f", obj.has("amount") ? obj.get("amount").getAsDouble() : 0),
                            status,
                            timeStr
                    );
                    data.add(row);
                    if ("PENDING".equals(status)) pendingCount++;
                }

                final int pending = pendingCount;
                Platform.runLater(() -> {
                    tblDepositRequests.setItems(data);
                    // Badge đỏ hiển thị số yêu cầu chờ duyệt
                    if (lblPendingBadge != null) {
                        if (pending > 0) {
                            lblPendingBadge.setText(pending + " chờ duyệt");
                            lblPendingBadge.setVisible(true);
                        } else {
                            lblPendingBadge.setVisible(false);
                        }
                    }
                    setDepositMsg("✅ Đã tải " + data.size() + " yêu cầu (" + pending + " đang chờ).", true);
                });
            } catch (Exception e) {
                setDepositMsg("❌ Lỗi parse: " + e.getMessage(), false);
            }
        });
    }

    /**
     * Admin duyệt yêu cầu đang được chọn trong bảng.
     * Gửi lệnh APPROVE_DEPOSIT:<id> lên server.
     * Server sẽ: nạp tiền vào ví user → đánh dấu request là APPROVED.
     */
    @FXML
    public void handleApproveDeposit() {
        ObservableList<String> selected = tblDepositRequests.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isEmpty()) {
            setDepositMsg("⚠ Vui lòng chọn một yêu cầu từ bảng.", false);
            return;
        }

        String reqId    = selected.get(0);
        String username = selected.size() > 1 ? selected.get(1) : "?";
        String amount   = selected.size() > 2 ? selected.get(2) : "?";
        String status   = selected.size() > 3 ? selected.get(3) : "";

        if (!"PENDING".equals(status)) {
            setDepositMsg("⚠ Yêu cầu #" + reqId + " đã được xử lý (" + status + ").", false);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận duyệt");
        confirm.setHeaderText("Duyệt yêu cầu nạp tiền #" + reqId);
        confirm.setContentText("Nạp " + amount + " vào ví của " + username + "?\n\nThao tác không thể hoàn tác.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                runInBackground("APPROVE_DEPOSIT:" + reqId, response -> {
                    if (response != null && response.startsWith("SUCCESS:")) {
                        String msg = response.substring("SUCCESS:".length());
                        setDepositMsg("✅ " + msg, true);
                        handleLoadDepositRequests(); // refresh bảng
                        handleLoadUsers();           // cập nhật số dư trong Tab 1
                    } else {
                        setDepositMsg("❌ " + parseFailReason(response), false);
                    }
                });
            }
        });
    }

    /**
     * Admin từ chối yêu cầu đang được chọn.
     * Gửi lệnh REJECT_DEPOSIT:<id> lên server.
     */
    @FXML
    public void handleRejectDeposit() {
        ObservableList<String> selected = tblDepositRequests.getSelectionModel().getSelectedItem();
        if (selected == null || selected.isEmpty()) {
            setDepositMsg("⚠ Vui lòng chọn một yêu cầu từ bảng.", false);
            return;
        }

        String reqId    = selected.get(0);
        String username = selected.size() > 1 ? selected.get(1) : "?";
        String status   = selected.size() > 3 ? selected.get(3) : "";

        if (!"PENDING".equals(status)) {
            setDepositMsg("⚠ Yêu cầu #" + reqId + " đã được xử lý (" + status + ").", false);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận từ chối");
        confirm.setHeaderText("Từ chối yêu cầu #" + reqId + " của " + username);
        confirm.setContentText("Người dùng sẽ không nhận được tiền. Xác nhận từ chối?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                runInBackground("REJECT_DEPOSIT:" + reqId, response -> {
                    if (response != null && response.startsWith("SUCCESS")) {
                        setDepositMsg("✅ Đã từ chối yêu cầu #" + reqId + " của " + username + ".", true);
                        handleLoadDepositRequests();
                    } else {
                        setDepositMsg("❌ " + parseFailReason(response), false);
                    }
                });
            }
        });
    }

    /**
     * Admin điều chỉnh số dư thủ công cho một user.
     * Gửi lệnh ADJUST_BALANCE:<username>:<newBalance> lên server.
     * Dùng setBalance (ghi đè, không cộng thêm).
     */
    @FXML
    public void handleAdjustBalance() {
        String username = txtAdjustUsername.getText().trim();
        String amountStr = txtAdjustAmount.getText().trim();

        if (username.isEmpty()) {
            setAdjustMsg("⚠ Vui lòng nhập username.", false);
            return;
        }
        if (amountStr.isEmpty()) {
            setAdjustMsg("⚠ Vui lòng nhập số dư mới.", false);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount < 0) {
                setAdjustMsg("⚠ Số dư không được âm.", false);
                return;
            }
        } catch (NumberFormatException e) {
            setAdjustMsg("⚠ Số dư không hợp lệ: " + amountStr, false);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận điều chỉnh số dư");
        confirm.setHeaderText("Đặt số dư của '" + username + "'");
        confirm.setContentText(String.format(
                "Số dư mới sẽ được đặt thành $%,.2f\n\n"
                        + "⚠ Đây là thao tác GHI ĐÈ (không cộng thêm).", amount));
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                runInBackground("ADJUST_BALANCE:" + username + ":" + amount, response -> {
                    if (response != null && response.startsWith("SUCCESS:")) {
                        String msg = response.substring("SUCCESS:".length());
                        setAdjustMsg("✅ " + msg, true);
                        Platform.runLater(() -> {
                            txtAdjustUsername.clear();
                            txtAdjustAmount.clear();
                        });
                        handleLoadUsers(); // làm mới bảng người dùng để hiện số dư mới
                    } else {
                        setAdjustMsg("❌ " + parseFailReason(response), false);
                    }
                });
            }
        });
    }

    /**
     * Điền username và số dư hiện tại từ dòng đang chọn trong bảng Người dùng (Tab 1)
     * vào form Điều chỉnh số dư để admin sửa nhanh.
     */
    @FXML
    public void handleFillFromSelected() {
        ObservableList<String> selected = tblUsers.getSelectionModel().getSelectedItem();
        if (selected == null || selected.size() < 5) {
            setAdjustMsg("⚠ Hãy chọn một người dùng trong Tab 'Quản lý Người dùng' trước.", false);
            return;
        }
        String username     = selected.get(1);
        String balanceRaw   = selected.get(4).replace("$", "").trim(); // bỏ ký hiệu $
        Platform.runLater(() -> {
            txtAdjustUsername.setText(username);
            txtAdjustAmount.setText(balanceRaw);
            setAdjustMsg("ℹ Đã điền thông tin của '" + username
                    + "'. Hãy sửa số dư và nhấn 'Cập nhật'.", true);
        });
    }

    // =========================================================================
    // NAVIGATION
    // =========================================================================

    @FXML
    public void handleBackToDashboard(ActionEvent event) {
        try {
            navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // MỞ MÀN HÌNH VÍ TIỀN
    // =========================================================================
    @FXML
    public void handleWallet(ActionEvent event) {
        try {
            URL walletUrl = getClass().getResource("/Wallet.fxml");
            if (walletUrl == null) {
                showAlert("Lỗi", "Không tìm thấy file Wallet.fxml.");
                return;
            }
            WalletController walletController = new WalletController();
            FXMLLoader loader = new FXMLLoader(walletUrl);
            loader.setController(walletController);
            Parent root = loader.load();
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Ví của tôi");
            javafx.scene.Scene currentScene = stage.getScene();
            if (currentScene != null) {
                currentScene.setRoot(root);
            } else {
                stage.setScene(new Scene(root));
            }
            stage.show();
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Lỗi mở Ví", "Không thể mở màn hình Ví:\n" + ex.getMessage());
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Chạy lệnh server trong background thread, callback trả về trên background thread. */
    private void runInBackground(String command, java.util.function.Consumer<String> callback) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return ClientService.sendRequest(command);
            }
        };
        task.setOnSucceeded(e -> callback.accept(task.getValue()));
        task.setOnFailed(e -> callback.accept(null));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /** Bind cột TableView theo index trong ObservableList<String>. */
    private void bindColumn(TableColumn<ObservableList<String>, String> col, int index) {
        if (col == null) return;
        col.setCellValueFactory(data -> {
            ObservableList<String> row = data.getValue();
            return new SimpleStringProperty(row.size() > index ? row.get(index) : "");
        });
    }

    /** Lấy lý do lỗi từ response server. */
    private String parseFailReason(String response) {
        if (response == null) return "Không nhận được phản hồi từ server.";
        if (response.startsWith("FAIL:")) return response.substring(5);
        return "Lỗi không xác định.";
    }

    private void setUserMsg(String msg, boolean success) {
        Platform.runLater(() -> {
            lblUserMsg.setText(msg);
            lblUserMsg.setStyle("-fx-font-size: 13px; -fx-font-style: italic; -fx-text-fill: "
                    + (success ? "#27ae60;" : "#e74c3c;"));
        });
    }

    private void setItemMsg(String msg, boolean success) {
        Platform.runLater(() -> {
            lblItemMsg.setText(msg);
            lblItemMsg.setStyle("-fx-font-size: 13px; -fx-font-style: italic; -fx-text-fill: "
                    + (success ? "#27ae60;" : "#e74c3c;"));
        });
    }

    private void setDepositMsg(String msg, boolean success) {
        Platform.runLater(() -> {
            if (lblDepositMsg == null) return;
            lblDepositMsg.setText(msg);
            lblDepositMsg.setStyle("-fx-font-size: 13px; -fx-font-style: italic; -fx-text-fill: "
                    + (success ? "#27ae60;" : "#e74c3c;"));
        });
    }

    private void setAdjustMsg(String msg, boolean success) {
        Platform.runLater(() -> {
            if (lblAdjustMsg == null) return;
            lblAdjustMsg.setText(msg);
            lblAdjustMsg.setStyle("-fx-font-size: 13px; -fx-font-style: italic; -fx-text-fill: "
                    + (success ? "#27ae60;" : "#e74c3c;"));
        });
    }

    /** Đọc field từ JsonObject, trả "" nếu null/không tồn tại. */
    private String safeGet(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el == null || el.isJsonNull()) ? "" : el.getAsString();
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}