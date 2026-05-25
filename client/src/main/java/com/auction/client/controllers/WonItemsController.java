package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.lang.reflect.Type;
import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Controller cho màn hình Giỏ hàng (WonItems.fxml).
 *
 * Hiển thị danh sách sản phẩm mà Bidder đã đấu giá thắng
 * (status = FINISHED hoặc PAID, highest_bidder = currentUsername).
 *
 * Giao tiếp với server qua lệnh GET_WON_ITEMS.
 */
public class WonItemsController implements Initializable {

    @FXML private VBox  wonList;
    @FXML private VBox  emptyState;
    @FXML private Label lblCount;
    @FXML private Label lblTotalSpent;

    private final Gson           gson     = new Gson();
    private final NavigationUtils navUtils = new NavigationUtils();
    private final NumberFormat   currency =
            NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    // -------------------------------------------------------------------------
    // KHỞI TẠO
    // -------------------------------------------------------------------------
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadWonItems();
    }

    // -------------------------------------------------------------------------
    // TẢI DỮ LIỆU TỪ SERVER
    // -------------------------------------------------------------------------
    private void loadWonItems() {
        Task<List<AuctionItem>> task = new Task<>() {
            @Override
            protected List<AuctionItem> call() {
                String response = ClientService.sendRequest("GET_WON_ITEMS");
                if (response == null || !response.startsWith("SUCCESS:")) return null;
                String json = response.substring("SUCCESS:".length());
                try {
                    Type listType = new TypeToken<List<AuctionItem>>() {}.getType();
                    return gson.fromJson(json, listType);
                } catch (Exception e) {
                    System.err.println("[WonItems] Lỗi parse JSON: " + e.getMessage());
                    return null;
                }
            }
        };

        task.setOnSucceeded(e -> {
            List<AuctionItem> items = task.getValue();
            Platform.runLater(() -> renderWonItems(items));
        });

        task.setOnFailed(e -> System.err.println(
                "[WonItems] Lỗi tải dữ liệu: " + task.getException()));

        Thread t = new Thread(task, "LoadWonItemsThread");
        t.setDaemon(true);
        t.start();
    }

    // -------------------------------------------------------------------------
    // RENDER DANH SÁCH
    // -------------------------------------------------------------------------
    private void renderWonItems(List<AuctionItem> items) {
        wonList.getChildren().clear();

        if (items == null || items.isEmpty()) {
            emptyState.setVisible(true);
            emptyState.setManaged(true);
            lblCount.setText("0 sản phẩm");
            lblTotalSpent.setText("$0,00");
            return;
        }

        emptyState.setVisible(false);
        emptyState.setManaged(false);

        double total = 0;
        for (AuctionItem item : items) {
            wonList.getChildren().add(buildRow(item));
            total += item.getCurrentHighestBid();
        }

        lblCount.setText(items.size() + " sản phẩm đã thắng");
        lblTotalSpent.setText(String.format("$%,.2f", total));
    }

    // -------------------------------------------------------------------------
    // XÂY DỰNG TỪNG DÒNG SẢN PHẨM
    // -------------------------------------------------------------------------
    private HBox buildRow(AuctionItem item) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14, 18, 14, 18));
        row.setStyle("-fx-background-color: white; -fx-background-radius: 12;"
                + "-fx-border-color: #E5E7EB; -fx-border-radius: 12; -fx-border-width: 1;");

        // ── Ảnh sản phẩm ───────────────────────────────────────────────────
        StackPane imgBox = new StackPane();
        imgBox.setPrefSize(80, 70);
        imgBox.setMinSize(80, 70);
        imgBox.setMaxSize(80, 70);
        imgBox.setStyle("-fx-background-color: #F3F4F6; -fx-background-radius: 8;");

        ImageView imgView = new ImageView();
        imgView.setFitWidth(76);
        imgView.setFitHeight(66);
        imgView.setPreserveRatio(true);

        String path = item.getImagePath();
        if (path != null && !path.isBlank()) {
            try {
                imgView.setImage(new Image("file:" + path, true));
            } catch (Exception ignored) { /* giữ ảnh trống */ }
        }
        imgBox.getChildren().add(imgView);

        // ── Thông tin sản phẩm ─────────────────────────────────────────────
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label lblName = new Label(item.getName());
        lblName.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #111827;");
        lblName.setWrapText(true);

        Label lblCategory = new Label(
                item.getCategory() != null ? "📦 " + item.getCategory() : "");
        lblCategory.setStyle("-fx-font-size: 11; -fx-text-fill: #9CA3AF;");

        Label lblBidder = new Label("🏆 Đấu giá thắng với giá:");
        lblBidder.setStyle("-fx-font-size: 11; -fx-text-fill: #6B7280;");

        Label lblPrice = new Label(String.format("$%,.2f", item.getCurrentHighestBid()));
        lblPrice.setStyle("-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: #27ae60;");

        info.getChildren().addAll(lblName, lblCategory, lblBidder, lblPrice);

        // ── Trạng thái + ngày kết thúc ─────────────────────────────────────
        VBox statusBox = new VBox(6);
        statusBox.setAlignment(Pos.CENTER_RIGHT);
        statusBox.setMinWidth(120);

        String statusText = "PAID".equals(item.getStatus()) ? "✅ Đã thanh toán" : "🏁 Đã kết thúc";
        String statusColor = "PAID".equals(item.getStatus()) ? "#27ae60" : "#e67e22";
        Label lblStatus = new Label(statusText);
        lblStatus.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: "
                + statusColor + ";");

        Label lblDate = new Label("📅 " + item.getEndTimeFormatted());
        lblDate.setStyle("-fx-font-size: 11; -fx-text-fill: #9CA3AF;");

        statusBox.getChildren().addAll(lblStatus, lblDate);

        row.getChildren().addAll(imgBox, info, statusBox);
        return row;
    }

    // -------------------------------------------------------------------------
    // SỰ KIỆN NÚT
    // -------------------------------------------------------------------------
    @FXML
    public void handleRefresh(ActionEvent event) {
        wonList.getChildren().clear();
        loadWonItems();
    }

    @FXML
    public void handleBack(ActionEvent event) {
        try {
            navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}