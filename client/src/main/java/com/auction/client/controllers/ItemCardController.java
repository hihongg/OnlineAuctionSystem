package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.io.File;
import java.net.URL;

public class ItemCardController {
    @FXML public VBox      cardContainer;
    @FXML public ImageView imgProduct;
    @FXML public Label     lblName;
    @FXML public Label     lblPrice;
    @FXML public Label     lblTime;

    public void setData(AuctionItem item, Runnable onClickAction) {
        lblName.setText(item.getName());
        lblPrice.setText(String.format("$%.2f", item.getCurrentBid()));
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime startTime = item.getStartTimeEpoch() > 0
                ? java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(item.getStartTimeEpoch()),
                java.time.ZoneId.systemDefault())
                : null;

        String statusText;
        if (item.getEndTime().isBefore(now)) {
            statusText = "🔴 Đã kết thúc";
        } else if (startTime != null && startTime.isAfter(now)) {
            statusText = "🟡 Sắp diễn ra";
        } else {
            statusText = "🟢 Đang diễn ra";
        }
        lblTime.setText(statusText);

        loadImage(item);

        // Sự kiện click và Hover
        cardContainer.setOnMouseClicked(event -> onClickAction.run());
        cardContainer.setOnMouseEntered(e -> cardContainer.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 15; " +
                        "-fx-cursor: hand; -fx-border-color: #0064D2; -fx-border-radius: 12; -fx-border-width: 1.5;"));
        cardContainer.setOnMouseExited(e -> cardContainer.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 15; " +
                        "-fx-cursor: hand; -fx-border-color: #E5E7EB; -fx-border-radius: 12; -fx-border-width: 1;"));
    }

    /**
     * Load ảnh theo thứ tự ưu tiên:
     *   1. imagePath từ server (đường dẫn file thật ngoài ổ đĩa, vd: uploads/abc.png)
     *   2. Ảnh mặc định theo category (nằm trong classpath/resources)
     *   3. Ảnh fallback chung
     *
     * FIX: Trước đây chỉ dùng getClass().getResource() — chỉ tìm được trong classpath (JAR).
     * Ảnh upload từ người dùng nằm ngoài JAR (thư mục uploads/ bên cạnh server).
     * Cần dùng new File(path).toURI() để load file từ ổ đĩa thật.
     */
    private void loadImage(AuctionItem item) {
        String imagePath = item.getImagePath();

        // Ưu tiên 1: imagePath từ server (file ngoài ổ đĩa)
        if (imagePath != null && !imagePath.isEmpty()) {
            // Thử load file từ đường dẫn tuyệt đối / tương đối trên ổ đĩa
            try {
                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    imgProduct.setImage(new Image(imageFile.toURI().toString()));
                    return;
                }
            } catch (Exception e) {
                System.out.println("[Card] Không load được file ảnh: " + imagePath + " — " + e.getMessage());
            }

            // Thử load từ classpath (trường hợp imagePath là đường dẫn resource)
            try {
                URL imageUrl = getClass().getResource(imagePath);
                if (imageUrl != null) {
                    imgProduct.setImage(new Image(imageUrl.toExternalForm()));
                    return;
                }
            } catch (Exception e) {
                System.out.println("[Card] Không load được resource ảnh: " + imagePath);
            }
        }

        // Ưu tiên 2: ảnh mặc định theo category
        String defaultPath = getDefaultImageByCategory(item.getCategory());
        try {
            URL defaultUrl = getClass().getResource(defaultPath);
            if (defaultUrl != null) {
                imgProduct.setImage(new Image(defaultUrl.toExternalForm()));
                return;
            }
        } catch (Exception ignored) { }

        // Ưu tiên 3: fallback
        loadFallbackImage();
    }

    private String getDefaultImageByCategory(String category) {
        if (category == null) return "/images/lap.jpg";
        switch (category.toUpperCase()) {
            case "ELECTRONICS": return "/images/lap.jpg";
            case "ART":         return "/images/wooting.jpg";
            case "VEHICLE":     return "/images/chuot.jpg";
            default:            return "/images/lap.jpg";
        }
    }

    private void loadFallbackImage() {
        try {
            URL fallback = getClass().getResource("/images/lap.jpg");
            if (fallback != null) {
                imgProduct.setImage(new Image(fallback.toExternalForm()));
            }
        } catch (Exception ignored) { }
    }
}