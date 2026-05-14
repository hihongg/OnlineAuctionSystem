package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.ClientSocketManager;
import com.auction.client.utils.NavigationUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.lang.reflect.Type;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MainDashboardController implements Initializable {

    @FXML
    private FlowPane itemGrid;

    private final Gson gson = new Gson();

    // =========================================================================
    // KHỞI TẠO
    // =========================================================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadAuctionItemsFromServer();
    }

    // =========================================================================
    // LẤY DANH SÁCH SẢN PHẨM TỪ SERVER (background thread)
    // =========================================================================
    private void loadAuctionItemsFromServer() {
        // Gửi request trên background thread — không block JavaFX thread
        new Thread(() -> {
            String response = ClientSocketManager.getInstance().sendRequest("GET_ITEMS");

            Platform.runLater(() -> {
                if (response == null || response.startsWith("FAIL")
                        || response.equals("TIMEOUT") || response.equals("NOT_CONNECTED")) {
                    showError("Không thể tải danh sách sản phẩm.\nKiểm tra server đã chạy chưa.");
                    return;
                }

                // response dạng: "SUCCESS:[{...},{...},...]"
                String json = response.substring("SUCCESS:".length());
                try {
                    Type listType = new TypeToken<List<AuctionItem>>(){}.getType();
                    List<AuctionItem> items = gson.fromJson(json, listType);

                    itemGrid.getChildren().clear();
                    if (items == null || items.isEmpty()) {
                        showError("Hiện chưa có phiên đấu giá nào đang chạy.");
                        return;
                    }

                    for (AuctionItem item : items) {
                        itemGrid.getChildren().add(createItemCard(item));
                    }
                } catch (Exception e) {
                    System.err.println("[DASHBOARD] Lỗi parse JSON: " + e.getMessage());
                    showError("Lỗi đọc dữ liệu từ server.");
                }
            });
        }, "LoadItemsThread").start();
    }

    // =========================================================================
    // TẠO CARD CHO TỪNG SẢN PHẨM
    // =========================================================================
    private VBox createItemCard(AuctionItem item) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-border-color: #dcdde1; -fx-border-radius: 8;"
                + " -fx-background-radius: 8; -fx-background-color: white;");
        card.setPrefWidth(220);
        card.setAlignment(Pos.CENTER_LEFT);

        // --- Tên sản phẩm ---
        Label nameLabel = new Label(item.getName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #2f3640;");
        nameLabel.setWrapText(true);

        // --- Giá hiện tại ---
        Label priceLabel = new Label(String.format("Giá hiện tại: $%.2f", item.getCurrentBid()));
        priceLabel.setStyle("-fx-text-fill: #e1b12c; -fx-font-weight: bold; -fx-font-size: 14px;");

        // --- Người dẫn đầu ---
        String bidder = item.getHighestBidder();
        Label winnerLabel = new Label("Dẫn đầu: " + (bidder != null ? bidder : "Chưa có"));
        winnerLabel.setStyle("-fx-text-fill: #487eb0; -fx-font-size: 12px;");

        // --- Đồng hồ đếm ngược (dùng endTimeMs — milliseconds) ---
        Label timeLabel = new Label();
        timeLabel.setStyle("-fx-text-fill: #e84118; -fx-font-size: 13px; -fx-font-weight: bold;");

        Button bidButton = new Button("Xem chi tiết");
        bidButton.setMaxWidth(Double.MAX_VALUE);
        bidButton.setStyle("-fx-background-color: #00a8ff; -fx-text-fill: white;"
                + " -fx-cursor: hand; -fx-font-weight: bold;");

        // Đồng hồ đếm ngược mỗi 1 giây
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long remaining = item.getEndTimeMs() - System.currentTimeMillis();
            if (remaining > 0) {
                long hours   = remaining / 3_600_000;
                long minutes = (remaining % 3_600_000) / 60_000;
                long seconds = (remaining % 60_000) / 1_000;
                timeLabel.setText(String.format("⏱ Còn lại: %02d:%02d:%02d", hours, minutes, seconds));
                // Đỏ tươi khi còn dưới 60 giây
                if (remaining < 60_000) {
                    timeLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 13px;"
                            + " -fx-font-weight: bold;");
                }
            } else {
                timeLabel.setText("⏱ Đã kết thúc");
                timeLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;"
                        + " -fx-font-style: italic;");
                bidButton.setDisable(true);
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        // --- Nút chuyển sang màn hình chi tiết ---
        bidButton.setOnAction(event -> openItemDetail(event, item));

        card.getChildren().addAll(nameLabel, priceLabel, winnerLabel, timeLabel, bidButton);
        return card;
    }

    // =========================================================================
    // CHUYỂN SANG MÀN HÌNH CHI TIẾT
    // =========================================================================
    private void openItemDetail(javafx.event.ActionEvent event, AuctionItem item) {
        try {
            URL url = getClass().getResource("/ItemDetail.fxml");
            if (url == null) {
                System.err.println("[DASHBOARD] Không tìm thấy ItemDetail.fxml!");
                return;
            }

            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();

            ItemDetailController detailController = loader.getController();
            detailController.setAuctionItem(item);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[DASHBOARD] Lỗi chuyển sang ItemDetail");
        }
    }

    // =========================================================================
    // HIỂN THỊ THÔNG BÁO LỖI TRONG GRID
    // =========================================================================
    private void showError(String message) {
        Label errorLabel = new Label(message);
        errorLabel.setStyle("-fx-text-fill: #e84118; -fx-font-size: 14px; -fx-padding: 20;");
        errorLabel.setWrapText(true);
        itemGrid.getChildren().clear();
        itemGrid.getChildren().add(errorLabel);
    }

    // =========================================================================
    // ĐĂNG XUẤT
    // =========================================================================
    @FXML
    public void handleLogout(ActionEvent event) {
        try {
            new NavigationUtils().switchScene(event, "/LoginView.fxml",
                    "Online Auction System - Login");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[DASHBOARD] Lỗi logout");
        }
    }
}