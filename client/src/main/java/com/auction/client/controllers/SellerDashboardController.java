package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller màn hình "Quản lý sản phẩm của tôi" (dành cho Seller).
 *
 * Chức năng:
 * - Xem toàn bộ sản phẩm mình đã đăng (GET_MY_ITEMS)
 * - Lọc theo trạng thái (OPEN / RUNNING / FINISHED / ...)
 * - Sửa sản phẩm ở trạng thái OPEN (navigate đến EditItem.fxml)
 * - Xóa / Hủy sản phẩm không đang RUNNING (DELETE_ITEM)
 * - Thống kê nhanh theo trạng thái
 */
public class SellerDashboardController implements Initializable {

    // ── FXML Controls ──────────────────────────────────────────────────────
    @FXML public Label           lblItemCount;
    @FXML public Label           lblStatus;
    @FXML public ComboBox<String> cmbFilter;

    @FXML public TableView<AuctionItem>        tblItems;
    @FXML public TableColumn<AuctionItem, Integer> colId;
    @FXML public TableColumn<AuctionItem, String>  colName;
    @FXML public TableColumn<AuctionItem, String>  colCategory;
    @FXML public TableColumn<AuctionItem, String>  colStartPrice;
    @FXML public TableColumn<AuctionItem, String>  colCurrentBid;
    @FXML public TableColumn<AuctionItem, String>  colStatus;
    @FXML public TableColumn<AuctionItem, String>  colEndTime;
    @FXML public TableColumn<AuctionItem, Void>    colActions;

    @FXML public Label lblStatOpen;
    @FXML public Label lblStatRunning;
    @FXML public Label lblStatFinished;
    @FXML public Label lblStatCanceled;

    // ── Internal ───────────────────────────────────────────────────────────
    private final NavigationUtils navUtils = new NavigationUtils();
    private final Gson            gson     = new Gson();
    private final ObservableList<AuctionItem> masterData   = FXCollections.observableArrayList();
    private final ObservableList<AuctionItem> filteredData = FXCollections.observableArrayList();

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ── Initialize ─────────────────────────────────────────────────────────
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupColumns();
        setupFilter();
        loadMyItems();
    }

    // ── Cài đặt cột TableView ──────────────────────────────────────────────
    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));

        colName.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getName()));

        colCategory.setCellValueFactory(data ->
                new SimpleStringProperty(translateCategory(data.getValue().getCategory())));

        colStartPrice.setCellValueFactory(data ->
                new SimpleStringProperty(String.format("$%,.2f", data.getValue().getStartingPrice())));

        colCurrentBid.setCellValueFactory(data ->
                new SimpleStringProperty(String.format("$%,.2f", data.getValue().getCurrentHighestBid())));

        colStatus.setCellValueFactory(data ->
                new SimpleStringProperty(translateStatus(data.getValue().getStatus())));

        // THIẾT LẬP TỰ ĐỘNG GIÃN CỘT BẢNG Ở ĐÂY
        tblItems.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Tô màu ô trạng thái
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    setStyle(statusColor(getTableRow() != null
                            && getTableRow().getItem() != null
                            ? getTableRow().getItem().getStatus() : ""));
                }
            }
        });

        colEndTime.setCellValueFactory(data -> {
            long epoch = data.getValue().getEndTimeEpoch();
            if (epoch <= 0) return new SimpleStringProperty("N/A");
            LocalDateTime ldt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(epoch), ZoneId.systemDefault());
            return new SimpleStringProperty(ldt.format(DT_FMT));
        });

        // Cột thao tác: nút Sửa + Xóa
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit   = new Button("✏ Sửa");
            private final Button btnDelete = new Button("🗑 Xóa");
            private final HBox   box       = new HBox(8, btnEdit, btnDelete);

            {
                btnEdit.setStyle(
                        "-fx-background-color: #3665F3; -fx-text-fill: white;" +
                                "-fx-font-size: 11; -fx-padding: 4 10 4 10;" +
                                "-fx-background-radius: 12; -fx-cursor: hand;");
                btnDelete.setStyle(
                        "-fx-background-color: #c0392b; -fx-text-fill: white;" +
                                "-fx-font-size: 11; -fx-padding: 4 10 4 10;" +
                                "-fx-background-radius: 12; -fx-cursor: hand;");

                btnEdit.setOnAction(e -> {
                    AuctionItem item = getTableRow().getItem();
                    if (item != null) handleEdit(item);
                });
                btnDelete.setOnAction(e -> {
                    AuctionItem item = getTableRow().getItem();
                    if (item != null) handleDelete(item);
                });
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    AuctionItem item = getTableRow().getItem();
                    if (item != null) {
                        // Chỉ cho sửa khi trạng thái OPEN
                        btnEdit.setDisable(!"OPEN".equals(item.getStatus()));
                        btnEdit.setOpacity("OPEN".equals(item.getStatus()) ? 1.0 : 0.4);
                        // Không cho xóa khi RUNNING
                        btnDelete.setDisable("RUNNING".equals(item.getStatus()));
                        btnDelete.setOpacity("RUNNING".equals(item.getStatus()) ? 0.4 : 1.0);
                    }
                    setGraphic(box);
                }
            }
        });
    }

    // ── Bộ lọc trạng thái ─────────────────────────────────────────────────
    private void setupFilter() {
        if (cmbFilter == null) return;
        cmbFilter.getItems().addAll("Tất cả", "Sắp diễn ra (OPEN)",
                "Đang chạy (RUNNING)", "Đã kết thúc (FINISHED)",
                "Đã thanh toán (PAID)", "Đã hủy (CANCELED)");
        cmbFilter.setValue("Tất cả");
        cmbFilter.setStyle(
                "-fx-background-color: #111111; -fx-text-fill: white;");
        cmbFilter.valueProperty().addListener((obs, o, n) -> applyFilter(n));
    }

    private void applyFilter(String filterText) {
        String raw = filterText == null ? "Tất cả" : filterText;
        List<AuctionItem> result;
        if ("Tất cả".equals(raw)) {
            result = masterData;
        } else {
            // Lấy mã trạng thái từ chuỗi hiển thị (vd: "Đang chạy (RUNNING)" → "RUNNING")
            String code = raw.replaceAll(".*\\((.+)\\).*", "$1");
            result = masterData.stream()
                    .filter(i -> code.equals(i.getStatus()))
                    .collect(Collectors.toList());
        }
        filteredData.setAll(result);
        tblItems.setItems(filteredData);
        updateStatusLabel(filteredData.size());
    }

    // ── Tải danh sách sản phẩm từ server ──────────────────────────────────
    private void loadMyItems() {
        setStatusLabel("⏳ Đang tải...", "#F8C938");

        Task<List<AuctionItem>> task = new Task<>() {
            @Override
            protected List<AuctionItem> call() {
                String resp = ClientService.sendRequest("GET_MY_ITEMS");
                if (resp == null || !resp.startsWith("SUCCESS:")) return null;
                String json = resp.substring("SUCCESS:".length());
                try {
                    Type t = new TypeToken<List<AuctionItem>>(){}.getType();
                    return gson.fromJson(json, t);
                } catch (Exception e) {
                    System.err.println("[SellerDash] Lỗi parse JSON: " + e.getMessage());
                    return null;
                }
            }
        };

        task.setOnSucceeded(e -> {
            List<AuctionItem> items = task.getValue();
            masterData.setAll(items != null ? items : List.of());
            applyFilter(cmbFilter.getValue());
            updateStats();
            updateItemCount();
            setStatusLabel("", "transparent");
        });

        task.setOnFailed(e -> setStatusLabel("❌ Không thể tải dữ liệu", "#e74c3c"));

        Thread t = new Thread(task, "SellerLoadThread");
        t.setDaemon(true);
        t.start();
    }

    // ── Xử lý sự kiện: Sửa sản phẩm ──────────────────────────────────────
    private void handleEdit(AuctionItem item) {
        if (!"OPEN".equals(item.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Không thể sửa",
                    "Chỉ có thể sửa sản phẩm ở trạng thái 'Sắp diễn ra (OPEN)'.\n" +
                            "Sản phẩm này đang ở trạng thái: " + translateStatus(item.getStatus()));
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/EditItem.fxml"));
            Parent root = loader.load();
            EditItemController ctrl = loader.getController();
            ctrl.setItem(item);
            Stage stage = (Stage) tblItems.getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (IOException ex) {
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể mở màn hình sửa: " + ex.getMessage());
        }
    }

    // ── Xử lý sự kiện: Xóa sản phẩm ──────────────────────────────────────
    private void handleDelete(AuctionItem item) {
        if ("RUNNING".equals(item.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Không thể xóa",
                    "Không thể xóa sản phẩm đang trong phiên đấu giá (RUNNING).");
            return;
        }

        String statusStr = translateStatus(item.getStatus());
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận xóa");
        confirm.setHeaderText("Xóa sản phẩm: " + item.getName());
        confirm.setContentText(
                "Trạng thái hiện tại: " + statusStr + "\n" +
                        "Hành động này không thể hoàn tác. Bạn có chắc chắn muốn xóa?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        setStatusLabel("⏳ Đang xóa...", "#F8C938");

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return ClientService.sendRequest("DELETE_ITEM:" + item.getId());
            }
        };

        task.setOnSucceeded(e -> {
            String resp = task.getValue();
            if (resp != null && resp.startsWith("SUCCESS:")) {
                showAlert(Alert.AlertType.INFORMATION, "Thành công",
                        "Đã xóa sản phẩm: " + item.getName());
                loadMyItems(); // Reload table
            } else {
                String msg = (resp != null && resp.startsWith("FAIL:"))
                        ? resp.substring(5) : "Lỗi không xác định";
                showAlert(Alert.AlertType.ERROR, "Xóa thất bại", msg);
                setStatusLabel("", "transparent");
            }
        });

        task.setOnFailed(e -> setStatusLabel("❌ Lỗi kết nối", "#e74c3c"));

        Thread t = new Thread(task, "DeleteItemThread");
        t.setDaemon(true);
        t.start();
    }

    // ── Handlers FXML ─────────────────────────────────────────────────────

    @FXML
    public void handleWallet(ActionEvent event) {
        try {
            // Khởi tạo loader và controller thủ công để tránh lỗi "No controller specified"
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Wallet.fxml"));

            WalletController walletController = new WalletController();
            loader.setController(walletController);

            // Load giao diện và thiết lập Scene
            Parent root = loader.load();
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();

            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
            stage.setTitle("Ví của tôi");
            stage.show();
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi mở Ví", "Không thể mở màn hình Ví:\n" + ex.getMessage());
        }
    }

    @FXML
    public void handleCreateNew(ActionEvent event) {
        try {
            navUtils.switchScene(event, "/CreateItem.fxml", "Đăng bán");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        masterData.clear();
        filteredData.clear();
        loadMyItems();
    }

    @FXML
    public void handleBack(ActionEvent event) {
        try {
            navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        ClientService.logout();
        try {
            navUtils.switchScene(event, "/LoginView.fxml", "Đăng nhập");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ── Helpers UI ────────────────────────────────────────────────────────
    private void updateStats() {
        long open     = masterData.stream().filter(i -> "OPEN".equals(i.getStatus())).count();
        long running  = masterData.stream().filter(i -> "RUNNING".equals(i.getStatus())).count();
        long finished = masterData.stream().filter(i ->
                "FINISHED".equals(i.getStatus()) || "PAID".equals(i.getStatus())).count();
        long canceled = masterData.stream().filter(i -> "CANCELED".equals(i.getStatus())).count();

        Platform.runLater(() -> {
            if (lblStatOpen     != null) lblStatOpen.setText("🔵 Sắp diễn ra: " + open);
            if (lblStatRunning  != null) lblStatRunning.setText("🟢 Đang chạy: " + running);
            if (lblStatFinished != null) lblStatFinished.setText("🟡 Kết thúc/Đã TT: " + finished);
            if (lblStatCanceled != null) lblStatCanceled.setText("🔴 Đã hủy: " + canceled);
        });
    }

    private void updateItemCount() {
        Platform.runLater(() -> {
            if (lblItemCount != null)
                lblItemCount.setText("Tổng: " + masterData.size() + " sản phẩm");
        });
    }

    private void updateStatusLabel(int count) {
        Platform.runLater(() -> {
            if (lblStatus != null)
                lblStatus.setText("Hiển thị: " + count + " sản phẩm");
        });
    }

    private void setStatusLabel(String msg, String color) {
        Platform.runLater(() -> {
            if (lblStatus != null) {
                lblStatus.setText(msg);
                lblStatus.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13;");
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    // ── Dịch trạng thái sang tiếng Việt ───────────────────────────────────
    private String translateStatus(String status) {
        if (status == null) return "N/A";
        return switch (status) {
            case "OPEN"     -> "Sắp diễn ra";
            case "RUNNING"  -> "Đang chạy";
            case "FINISHED" -> "Đã kết thúc";
            case "PAID"     -> "Đã thanh toán";
            case "CANCELED" -> "Đã hủy";
            default         -> status;
        };
    }

    private String translateCategory(String cat) {
        if (cat == null) return "N/A";
        return switch (cat) {
            case "ELECTRONICS" -> "Điện tử";
            case "ART"         -> "Nghệ thuật";
            case "VEHICLE"     -> "Phương tiện";
            default            -> cat;
        };
    }

    private String statusColor(String status) {
        return switch (status == null ? "" : status) {
            case "OPEN"     -> "-fx-text-fill: #3498db; -fx-font-weight: bold;";
            case "RUNNING"  -> "-fx-text-fill: #2ecc71; -fx-font-weight: bold;";
            case "FINISHED", "PAID" -> "-fx-text-fill: #F8C938;";
            case "CANCELED" -> "-fx-text-fill: #e74c3c;";
            default         -> "-fx-text-fill: #aaaaaa;";
        };
    }
}