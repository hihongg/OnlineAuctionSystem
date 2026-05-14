package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.ClientSocketManager;
import com.auction.client.utils.NavigationUtils;
import com.auction.shared.models.Item;
import com.auction.shared.models.Message;
import com.google.gson.Gson;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Controller màn hình chi tiết đấu giá (ItemDetail.fxml).
 *
 * Thay đổi quan trọng so với bản cũ:
 *   1. handlePlaceBid() → gửi lệnh PLACE_BID tới server thật (thay vì chỉ cập nhật local)
 *   2. Đăng ký broadcast listener → nhận BID_UPDATE realtime từ server
 *      (khi người khác đặt giá, UI tự cập nhật không cần refresh)
 *   3. Đồng hồ đếm ngược theo endTime thật từ server
 */
public class ItemDetailController implements Initializable {

    // =========================================================================
    // FXML BINDINGS
    // =========================================================================
    @FXML private Label     lblName;
    @FXML private Label     lblPrice;
    @FXML private Label     lblWinner;
    @FXML private Label     lblTime;
    @FXML private TextField txtBidAmount;
    @FXML private Label     lblMessage;

    // =========================================================================
    // STATE
    // =========================================================================
    private AuctionItem currentItem;  // model dùng cho UI (từ Dashboard)
    private int         currentItemId;
    private long        endTimeMs;    // milliseconds, đồng bộ từ server
    private Timeline    countdownTimer;
    private final Gson  gson = new Gson();

    /**
     * Broadcast listener — lưu vào field để có thể removeBroadcastListener() sau này.
     * Lambda hoặc method reference KHÔNG dùng được vì mỗi lần tạo là object khác nhau
     * → không remove được. Phải lưu cùng 1 reference.
     */
    private final Consumer<Message> broadcastListener = this::handleBroadcast;

    // =========================================================================
    // KHỞI TẠO
    // =========================================================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Đăng ký nhận broadcast từ server ngay khi màn hình mở
        ClientSocketManager.getInstance().addBroadcastListener(broadcastListener);
        System.out.println("[DETAIL] Đã đăng ký broadcast listener.");
    }

    /**
     * Được gọi từ MainDashboardController trước khi hiển thị màn hình này.
     * Truyền vào item đã chọn và đồng bộ dữ liệu mới nhất từ server.
     */
    public void setAuctionItem(AuctionItem item) {
        this.currentItem   = item;
        this.currentItemId = item.getId();
        this.endTimeMs     = item.getEndTimeMs();

        // Hiển thị dữ liệu ban đầu
        lblName.setText(item.getName());
        lblPrice.setText(String.format("Giá hiện tại: $%.2f", item.getCurrentBid()));
        lblWinner.setText("Người dẫn đầu: " + item.getHighestBidder());

        // Khởi động đồng hồ đếm ngược theo endTime từ server
        startCountdown();
    }

    // =========================================================================
    // ĐẶT GIÁ — gửi PLACE_BID tới server (chạy trên background thread)
    // =========================================================================
    @FXML
    public void handlePlaceBid(ActionEvent event) {
        String amountText = txtBidAmount.getText().trim();
        if (amountText.isEmpty()) {
            showError("Vui lòng nhập số tiền muốn đặt.");
            return;
        }

        double bidAmount;
        try {
            bidAmount = Double.parseDouble(amountText);
        } catch (NumberFormatException e) {
            showError("Vui lòng nhập số hợp lệ.");
            return;
        }

        if (bidAmount <= currentItem.getCurrentBid()) {
            showError(String.format("Giá đặt phải cao hơn $%.2f!", currentItem.getCurrentBid()));
            return;
        }

        // Gửi request trên background thread để không đóng băng UI
        final double finalBid = bidAmount;
        new Thread(() -> {
            String response = ClientSocketManager.getInstance()
                    .sendRequest("PLACE_BID:" + currentItemId + ":" + finalBid);

            // Cập nhật UI trở lại trên JavaFX thread
            javafx.application.Platform.runLater(() -> {
                if (response.startsWith("SUCCESS")) {
                    showSuccess(String.format("Đặt giá $%.2f thành công!", finalBid));
                    txtBidAmount.clear();
                    // Giá sẽ được cập nhật ngay qua broadcast BID_UPDATE từ server
                } else if ("TIMEOUT".equals(response) || "NOT_CONNECTED".equals(response)) {
                    showError("Lỗi kết nối. Kiểm tra lại server.");
                } else {
                    // response dạng "FAIL:<lý do>"
                    String reason = response.startsWith("FAIL:")
                            ? response.substring(5) : response;
                    showError("Thất bại: " + reason);
                }
            });
        }, "PlaceBidThread").start();
    }

    // =========================================================================
    // XỬ LÝ BROADCAST TỪ SERVER (được gọi bởi ClientSocketManager)
    // =========================================================================

    /**
     * Nhận mọi broadcast, lọc ra những cái liên quan đến item đang xem.
     * Phương thức này đã chạy trên JavaFX Application Thread (đảm bảo bởi ClientSocketManager).
     */
    private void handleBroadcast(Message msg) {
        switch (msg.getAction()) {
            case "BID_UPDATE":
                handleBidUpdate(msg.getPayload());
                break;
            case "AUCTION_ENDED":
                handleAuctionEnded(msg.getPayload());
                break;
            case "TIME_EXTENDED":
                handleTimeExtended(msg.getPayload());
                break;
            default:
                // Bỏ qua các broadcast khác (ví dụ: của item khác)
                break;
        }
    }

    /** Có bid mới → cập nhật giá và người dẫn đầu */
    private void handleBidUpdate(String payload) {
        try {
            Item updated = gson.fromJson(payload, Item.class);
            // Chỉ cập nhật nếu broadcast là về item đang xem
            if (updated.getId() != currentItemId) return;

            double newBid = updated.getCurrentHighestBid();
            String winner = updated.getCurrentHighestBidder();

            // Cập nhật state local
            currentItem.setCurrentBid(newBid);
            currentItem.setHighestBidder(winner);

            // Cập nhật UI
            lblPrice.setText(String.format("Giá hiện tại: $%.2f", newBid));
            lblWinner.setText("Người dẫn đầu: " + winner);

            System.out.println("[DETAIL] Giá mới: $" + newBid + " bởi " + winner);

        } catch (Exception e) {
            System.err.println("[DETAIL] Lỗi parse BID_UPDATE: " + e.getMessage());
        }
    }

    /** Phiên kết thúc → dừng đồng hồ, hiện kết quả */
    private void handleAuctionEnded(String payload) {
        if (countdownTimer != null) countdownTimer.stop();
        lblTime.setText("⏱ Phiên đã kết thúc!");
        lblTime.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
        lblMessage.setStyle("-fx-text-fill: #e84118;");
        lblMessage.setText(payload); // Server đã format sẵn thông báo kết quả
    }

    /** Anti-sniping → cập nhật endTime mới */
    private void handleTimeExtended(String payload) {
        try {
            // payload: {"itemId":1,"newEndTime":1234567890}
            TimeExtendedPayload ext = gson.fromJson(payload, TimeExtendedPayload.class);
            if (ext.itemId != currentItemId) return;

            endTimeMs = ext.newEndTime;
            System.out.println("[DETAIL] Phiên được gia hạn. EndTime mới: " + endTimeMs);
        } catch (Exception e) {
            System.err.println("[DETAIL] Lỗi parse TIME_EXTENDED: " + e.getMessage());
        }
    }

    // =========================================================================
    // ĐỒNG HỒ ĐẾM NGƯỢC (chạy mỗi giây, dùng endTimeMs từ server)
    // =========================================================================
    private void startCountdown() {
        if (countdownTimer != null) countdownTimer.stop();

        countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long remaining = endTimeMs - System.currentTimeMillis();
            if (remaining > 0) {
                long hours   = remaining / 3_600_000;
                long minutes = (remaining % 3_600_000) / 60_000;
                long seconds = (remaining % 60_000) / 1_000;
                lblTime.setText(String.format("⏱ Còn lại: %02d:%02d:%02d", hours, minutes, seconds));
                // Đổi màu đỏ khi còn dưới 60 giây
                if (remaining < 60_000) {
                    lblTime.setStyle("-fx-text-fill: #e84118; -fx-font-weight: bold;");
                }
            } else {
                lblTime.setText("⏱ Đã kết thúc");
                lblTime.setStyle("-fx-text-fill: #7f8c8d;");
                countdownTimer.stop();
            }
        }));
        countdownTimer.setCycleCount(Timeline.INDEFINITE);
        countdownTimer.play();
    }

    // =========================================================================
    // ĐIỀU HƯỚNG
    // =========================================================================
    @FXML
    public void handleBackToDashboard(ActionEvent event) {
        // Hủy listener trước khi rời màn hình — tránh memory leak
        ClientSocketManager.getInstance().removeBroadcastListener(broadcastListener);
        if (countdownTimer != null) countdownTimer.stop();

        try {
            new NavigationUtils().switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[DETAIL] Lỗi quay lại Dashboard");
        }
    }

    // =========================================================================
    // HELPER
    // =========================================================================
    private void showError(String msg) {
        lblMessage.setStyle("-fx-text-fill: #e84118;");
        lblMessage.setText(msg);
    }

    private void showSuccess(String msg) {
        lblMessage.setStyle("-fx-text-fill: #27ae60;");
        lblMessage.setText(msg);
    }

    // Inner class để parse payload TIME_EXTENDED
    private static class TimeExtendedPayload {
        int  itemId;
        long newEndTime;
    }
}