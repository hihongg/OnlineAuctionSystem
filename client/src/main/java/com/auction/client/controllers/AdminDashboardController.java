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
import java.util.ResourceBundle;

/**
 * Controller màn hình Admin Panel.
 *
 * Chức năng:
 *   Tab 1 — Quản lý Người dùng: xem danh sách, đổi role, xóa tài khoản.
 *   Tab 2 — Quản lý Sản phẩm : xem tất cả item, đổi trạng thái, xóa.
 *
 * Giao tiếp với server qua các lệnh:
 *   GET_ALL_USERS, DELETE_USER, UPDATE_USER_ROLE,
 *   GET_ALL_ITEMS, DELETE_ITEM, CHANGE_ITEM_STATUS
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

    private final NavigationUtils navUtils = new NavigationUtils();
    private final Gson gson = new Gson();

    // =========================================================================
    // KHỞI TẠO
    // =========================================================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (lblAdminName != null && ClientService.currentUsername != null) {
            lblAdminName.setText("Logged in: " + ClientService.currentUsername);
        }

        // Cài đặt ComboBox roles
        if (cmbNewRole != null) {
            cmbNewRole.getItems().addAll("BIDDER", "SELLER", "ADMIN");
            cmbNewRole.setValue("BIDDER");
        }

        // Cài đặt ComboBox item statuses (theo Item.Status)
        if (cmbItemStatus != null) {
            cmbItemStatus.getItems().addAll("OPEN", "RUNNING", "FINISHED", "PAID", "CANCELED");
            cmbItemStatus.setValue("OPEN");
        }

        // Bind cột cho bảng Users (server trả: [id, username, email, role, created_at, balance])
        bindColumn(colUserId,      0);
        bindColumn(colUsername,    1);
        bindColumn(colEmail,       2);
        bindColumn(colRole,        3);
        bindColumn(colBalance,     4);
        bindColumn(colUserCreated, 5);

        // Bind cột cho bảng Items
        bindColumn(colItemId,       0);
        bindColumn(colItemName,     1);
        bindColumn(colItemCategory, 2);
        bindColumn(colItemPrice,    3);
        bindColumn(colItemStatus,   4);
        bindColumn(colSellerId,     5);

        // Tải dữ liệu ngay khi mở
        handleLoadUsers();
        handleLoadItems();
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

        // Ngăn admin tự đổi role chính mình
        if (username.equals(ClientService.currentUsername)) {
            setUserMsg("⚠ Không thể tự đổi role của chính mình.", false);
            return;
        }

        runInBackground("UPDATE_USER_ROLE:" + username + ":" + newRole, response -> {
            if (response != null && response.startsWith("SUCCESS")) {
                setUserMsg("✅ Đã đổi role của '" + username + "' thành " + newRole + ".", true);
                handleLoadUsers(); // refresh
            } else {
                String reason = (response != null && response.startsWith("FAIL:"))
                        ? response.substring(5) : "Lỗi không xác định";
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

        // Xác nhận trước khi xóa
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
                        String reason = (response != null && response.startsWith("FAIL:"))
                                ? response.substring(5) : "Lỗi không xác định";
                        setUserMsg("❌ " + reason, false);
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
                String reason = (response != null && response.startsWith("FAIL:"))
                        ? response.substring(5) : "Lỗi không xác định";
                setItemMsg("❌ " + reason, false);
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

        // Cảnh báo đặc biệt nếu phiên đang chạy
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
                        String reason = (response != null && response.startsWith("FAIL:"))
                                ? response.substring(5) : "Lỗi không xác định";
                        setItemMsg("❌ " + reason, false);
                    }
                });
            }
        });
    }

    /**
     * Xóa hàng loạt tất cả sản phẩm đã kết thúc (FINISHED / PAID / CANCELED).
     * Chỉ Admin mới dùng được.
     */
    @FXML
    public void handleDeleteAllFinished() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận xóa hàng loạt");
        confirm.setHeaderText("Xóa tất cả sản phẩm đã kết thúc?");
        confirm.setContentText("Thao tác này sẽ xóa VĨNH VIỄN toàn bộ sản phẩm có trạng thái\n"
                + "FINISHED, PAID và CANCELED khỏi hệ thống.\n\n"
                + "⚠ Không thể hoàn tác!");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                runInBackground("ADMIN_DELETE_FINISHED", response -> {
                    if (response != null && response.startsWith("SUCCESS")) {
                        setItemMsg("✅ " + response.substring("SUCCESS:".length()), true);
                        handleLoadItems(); // refresh bảng
                    } else {
                        String reason = (response != null && response.startsWith("FAIL:"))
                                ? response.substring(5) : "Lỗi không xác định";
                        setItemMsg("❌ " + reason, false);
                    }
                });
            }
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
    // HELPERS
    // =========================================================================

    /** Chạy lệnh server trong background thread, callback trả về trên background thread.
     *  UI updates phải dùng Platform.runLater() bên trong callback. */
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

    /** Đọc field từ JsonObject, trả "" nếu null/không tồn tại. */
    private String safeGet(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el == null || el.isJsonNull()) ? "" : el.getAsString();
    }

    /** Hiển thị hộp thoại thông báo lỗi. */
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
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
            com.auction.client.controllers.WalletController walletController =
                    new com.auction.client.controllers.WalletController();
            FXMLLoader loader = new FXMLLoader(walletUrl);
            loader.setController(walletController);
            Parent root = loader.load();
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Ví của tôi");
            javafx.scene.Scene currentScene = stage.getScene();
            if (currentScene != null) {
                currentScene.setRoot(root);
            } else {
                stage.setScene(new javafx.scene.Scene(root));
            }
            stage.show();
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Lỗi mở Ví", "Không thể mở màn hình Ví:\n" + ex.getMessage());
        }
    }

}