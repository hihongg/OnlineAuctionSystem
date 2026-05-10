package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Node;
public class MainDashboardController implements Initializable {

    @FXML
    private FlowPane itemGrid;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadAuctionItems();
    }

    private void loadAuctionItems() {
        LocalDateTime now = LocalDateTime.now();

        // Cập nhật mock data: Thời gian kết thúc = Hiện tại + X giờ/phút
        List<AuctionItem> mockItems = Arrays.asList(
                new AuctionItem("Bàn phím cơ Wooting 60HE", 175.50, now.plusHours(2).plusMinutes(15)),
                new AuctionItem("Laptop Alienware x17 R2", 1200.00, now.plusHours(12)),
                new AuctionItem("Tài khoản Minecraft Premium", 25.00, now.plusMinutes(45)),
                new AuctionItem("Card đồ họa Intel Arc Graphics", 250.00, now.plusSeconds(10)) // Test thử kết thúc nhanh trong 10 giây
        );

        if (itemGrid != null) {
            itemGrid.getChildren().clear();
            for (AuctionItem item : mockItems) {
                VBox card = createItemCard(item);
                itemGrid.getChildren().add(card);
            }
        }
    }

    private VBox createItemCard(AuctionItem item) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-border-color: #dcdde1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: white;");
        card.setPrefWidth(220);
        card.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(item.getName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #2f3640;");
        nameLabel.setWrapText(true);

        Label priceLabel = new Label("Giá hiện tại: $" + item.getCurrentBid());
        priceLabel.setStyle("-fx-text-fill: #e1b12c; -fx-font-weight: bold; -fx-font-size: 14px;");

        // Label thời gian rỗng ban đầu, sẽ được Timeline cập nhật liên tục
        Label timeLabel = new Label();
        timeLabel.setStyle("-fx-text-fill: #e84118; -fx-font-size: 13px; -fx-font-weight: bold;");

        Button bidButton = new Button("Xem chi tiết");
        bidButton.setMaxWidth(Double.MAX_VALUE);
        bidButton.setStyle("-fx-background-color: #00a8ff; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");

        // --- LOGIC ĐẾM NGƯỢC (COUNTDOWN TIMELINE) ---
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime currentTime = LocalDateTime.now();

            if (currentTime.isAfter(item.getEndTime()) || currentTime.isEqual(item.getEndTime())) {
                timeLabel.setText("⏱ Đã kết thúc");
                timeLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px; -fx-font-style: italic;");
                bidButton.setDisable(true); // Vô hiệu hóa nút bấm khi hết giờ
            } else {
                long hours = ChronoUnit.HOURS.between(currentTime, item.getEndTime());
                long minutes = ChronoUnit.MINUTES.between(currentTime, item.getEndTime()) % 60;
                long seconds = ChronoUnit.SECONDS.between(currentTime, item.getEndTime()) % 60;

                timeLabel.setText(String.format("⏱ Còn lại: %02d:%02d:%02d", hours, minutes, seconds));
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE); // Chạy vô hạn cho đến khi dừng thủ công hoặc thẻ bị hủy
        timeline.play(); // Bắt đầu đếm ngược
        // --------------------------------------------

        // ... (phần code Timeline đếm ngược ở trên)
        // ... (phần code Timeline ở trên giữ nguyên)

        // ... (code đếm ngược Timeline phía trên giữ nguyên)

        // ... (code đếm ngược Timeline phía trên giữ nguyên)

        timeline.play(); // Bắt đầu đếm ngược

        // --- ĐOẠN CODE CHUYỂN CẢNH MỚI ---
        bidButton.setOnAction(event -> {
            try {
                java.net.URL url = getClass().getResource("/ItemDetail.fxml");
                System.out.println("Đường dẫn file FXML: " + url);

                if (url == null) {
                    System.out.println("❌ LỖI: Không tìm thấy file FXML!");
                    return;
                }

                // Chỉ khai báo loader và root 1 lần duy nhất ở đây
                FXMLLoader loader = new FXMLLoader(url);
                Parent root = loader.load();

                ItemDetailController detailController = loader.getController();
                detailController.setAuctionItem(item);

                Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Lỗi chuyển cảnh sang ItemDetail");
            }
        });

        // 2 dòng này để đưa các thành phần vào trong thẻ
        card.getChildren().addAll(nameLabel, priceLabel, timeLabel, bidButton);
        return card;
    } // <--- DẤU NGOẶC ĐÓNG HÀM createItemCard
    @FXML
    public void handleLogout(ActionEvent event) {
        try {
            // Gọi công cụ chuyển cảnh của bạn
            com.auction.client.utils.NavigationUtils navUtils = new com.auction.client.utils.NavigationUtils();
            navUtils.switchScene(event, "/LoginView.fxml", "Online Auction System - Login");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Lỗi khi quay lại màn hình Login");
        }
    }
}