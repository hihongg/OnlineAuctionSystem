package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import java.net.URL;

public class ItemCardController {
    @FXML private VBox cardContainer;
    @FXML private ImageView imgProduct;
    @FXML private Label lblName;
    @FXML private Label lblPrice;
    @FXML private Label lblTime;

    public void setData(AuctionItem item, Runnable onClickAction) {
        lblName.setText(item.getName());
        lblPrice.setText(String.format("$%.2f", item.getCurrentBid()));
        lblTime.setText(item.getEndTime().isBefore(java.time.LocalDateTime.now()) ? "🔴 Đã kết thúc" : "🟢 Đang diễn ra");

        // ĐỌC ẢNH CHUẨN (Hỗ trợ cả web và thư mục resources)
        if (item.getImagePath() != null && !item.getImagePath().isEmpty()) {
            try {
                // Tải ảnh local từ thư mục resources
                java.net.URL imageUrl = getClass().getResource(item.getImagePath());
                if (imageUrl != null) {
                    imgProduct.setImage(new Image(imageUrl.toExternalForm()));
                } else {
                    System.out.println("CẢNH BÁO: Không tìm thấy ảnh tại: " + item.getImagePath());
                }
            } catch (Exception e) {
                System.out.println("Lỗi load ảnh: " + e.getMessage());
            }
        }

        // Sự kiện click và Hover
        cardContainer.setOnMouseClicked(event -> onClickAction.run());
        cardContainer.setOnMouseEntered(e -> cardContainer.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 15; -fx-cursor: hand; -fx-border-color: #0064D2; -fx-border-radius: 12; -fx-border-width: 1.5;"));
        cardContainer.setOnMouseExited(e -> cardContainer.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 15; -fx-cursor: hand; -fx-border-color: #E5E7EB; -fx-border-radius: 12; -fx-border-width: 1;"));
    }
}