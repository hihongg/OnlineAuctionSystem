package com.auction.client.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import com.auction.client.models.AuctionItem;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class ItemDetailController {

    @FXML private Label lblName;
    @FXML private Label lblPrice;
    @FXML private Label lblTime;
    @FXML private TextField txtBidAmount;
    @FXML private Label lblMessage;

    private AuctionItem currentItem;

    // Hàm này được gọi từ MainDashboardController để truyền dữ liệu sang
    // Hàm này được gọi từ MainDashboardController để truyền dữ liệu sang
    public void setAuctionItem(AuctionItem item) {
        this.currentItem = item;
        lblName.setText(item.getName());
        lblPrice.setText("Giá hiện tại: $" + item.getCurrentBid());

        // --- BẮT ĐẦU ĐOẠN CODE ĐẾM NGƯỢC ---
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime currentTime = LocalDateTime.now();

            if (currentTime.isAfter(item.getEndTime()) || currentTime.isEqual(item.getEndTime())) {
                lblTime.setText("⏱ Đã kết thúc");
                lblTime.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 16px; -fx-font-style: italic;");
                txtBidAmount.setDisable(true); // Khóa luôn ô nhập tiền nếu đã hết giờ
            } else {
                long hours = ChronoUnit.HOURS.between(currentTime, item.getEndTime());
                long minutes = ChronoUnit.MINUTES.between(currentTime, item.getEndTime()) % 60;
                long seconds = ChronoUnit.SECONDS.between(currentTime, item.getEndTime()) % 60;

                lblTime.setText(String.format("⏱ Thời gian còn lại: %02d:%02d:%02d", hours, minutes, seconds));
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        // --- KẾT THÚC ĐOẠN CODE ĐẾM NGƯỢC ---
    }

    @FXML
    public void handlePlaceBid(ActionEvent event) {
        try {
            double bidAmount = Double.parseDouble(txtBidAmount.getText());
            if (bidAmount <= currentItem.getCurrentBid()) {
                lblMessage.setStyle("-fx-text-fill: red;");
                lblMessage.setText("Lỗi: Giá đặt phải cao hơn giá hiện tại!");
            } else {
                lblMessage.setStyle("-fx-text-fill: green;");
                lblMessage.setText("Thành công: Bạn đã đặt giá $" + bidAmount);
                // Cập nhật giao diện (Sau này sẽ gọi API cập nhật DB ở đây)
                lblPrice.setText("Giá hiện tại: $" + bidAmount);
                txtBidAmount.clear();
            }
        } catch (NumberFormatException e) {
            lblMessage.setStyle("-fx-text-fill: red;");
            lblMessage.setText("Lỗi: Vui lòng nhập số hợp lệ.");
        }
    }

    @FXML
    public void handleBackToDashboard(ActionEvent event) {
        try {
            // Gọi công cụ chuyển cảnh để về lại Dashboard
            com.auction.client.utils.NavigationUtils navUtils = new com.auction.client.utils.NavigationUtils();
            navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Lỗi khi quay lại Dashboard");
        }
    }
}