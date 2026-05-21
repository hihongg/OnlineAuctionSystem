package com.auction.client.controllers;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.net.URL;
import com.auction.client.models.AuctionItem;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class ItemDetailController {

    @FXML private Label lblName;
    @FXML private Label lblPrice;
    @FXML private Label lblTime;
    @FXML private TextField txtBidAmount;
    @FXML private Label lblMessage;

    @FXML private ImageView imgProduct;
    @FXML private TextArea txtDescription;

    private AuctionItem currentItem;

    public void setAuctionItem(AuctionItem item) {
        this.currentItem = item;
        lblName.setText(item.getName());
        lblPrice.setText("Giá hiện tại: $" + item.getCurrentBid());

        // Đổ dữ liệu Mô tả sản phẩm
        if (txtDescription != null) {
            txtDescription.setText(item.getDescription());
            txtDescription.setEditable(false);
            txtDescription.setWrapText(true);
        }

        // Đổ dữ liệu Ảnh lớn sản phẩm CHUẨN
        if (imgProduct != null && item.getImagePath() != null && !item.getImagePath().isEmpty()) {
            try {
                String path = item.getImagePath();
                if (path.startsWith("http")) {
                    imgProduct.setImage(new Image(path, true));
                } else {
                    URL imageUrl = getClass().getResource(path);
                    if (imageUrl != null) {
                        imgProduct.setImage(new Image(imageUrl.toExternalForm()));
                    } else {
                        System.out.println("Lỗi chi tiết: Không tìm thấy ảnh " + path);
                    }
                }
            } catch (Exception e) {
                System.out.println("Lỗi nạp ảnh lớn chi tiết: " + e.getMessage());
            }
        }

        // Đếm ngược thời gian
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime currentTime = LocalDateTime.now();
            if (currentTime.isAfter(item.getEndTime()) || currentTime.isEqual(item.getEndTime())) {
                lblTime.setText("⏱ Đã kết thúc");
                txtBidAmount.setDisable(true);
            } else {
                long hours = ChronoUnit.HOURS.between(currentTime, item.getEndTime());
                long minutes = ChronoUnit.MINUTES.between(currentTime, item.getEndTime()) % 60;
                long seconds = ChronoUnit.SECONDS.between(currentTime, item.getEndTime()) % 60;
                lblTime.setText(String.format("⏱ Thời gian còn lại: %02d:%02d:%02d", hours, minutes, seconds));
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
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
                lblPrice.setText("Giá hiện tại: $" + bidAmount);
                currentItem.setCurrentBid(bidAmount);
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
            com.auction.client.utils.NavigationUtils navUtils = new com.auction.client.utils.NavigationUtils();
            navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}