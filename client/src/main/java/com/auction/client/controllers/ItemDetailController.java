package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.ClientSocketManager;
import com.auction.client.utils.NavigationUtils;
import com.auction.shared.models.Item;
import com.auction.shared.models.Message;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.lang.reflect.Type;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Controller màn hình chi tiết đấu giá (ItemDetail.fxml).
 *
 * Thêm mới so với bản cũ:
 *   1. {@link #loadBidHistory} — tải lịch sử giá từ server, vẽ LineChart
 *   2. {@link #handleBidUpdate} — thêm điểm mới lên chart mỗi khi có BID_UPDATE
 *   3. {@link #handleRegisterAutoBid} — đăng ký auto-bid với server
 *   4. {@link #handleCancelAutoBid} — hủy auto-bid
 */
public class ItemDetailController implements Initializable {

    // =========================================================================
    // FXML BINDINGS — phần thông tin sản phẩm + đặt giá thủ công
    // =========================================================================
    @FXML private Label     lblName;
    @FXML private Label     lblDescription;
    @FXML private Label     lblPrice;
    @FXML private Label     lblWinner;
    @FXML private Label     lblTime;
    @FXML private TextField txtBidAmount;
    @FXML private Label     lblMessage;

    // =========================================================================
    // FXML BINDINGS — Auto-Bidding
    // =========================================================================
    @FXML private TextField txtMaxBid;
    @FXML private TextField txtIncrement;
    @FXML private Label     lblAutoBidStatus;

    // =========================================================================
    // FXML BINDINGS — Biểu đồ lịch sử giá
    // =========================================================================
    @FXML private LineChart<String, Number>  bidChart;
    @FXML private CategoryAxis               chartXAxis;
    @FXML private NumberAxis                 chartYAxis;
    @FXML private Label                      lblChartStatus;

    // =========================================================================
    // STATE
    // =========================================================================
    private AuctionItem currentItem;
    private int         currentItemId;
    private long        endTimeMs;
    private Timeline    countdownTimer;
    private final Gson  gson = new Gson();

    // Series duy nhất trên biểu đồ — thêm điểm vào đây khi có bid mới
    private final XYChart.Series<String, Number> bidSeries = new XYChart.Series<>();

    // Format timestamp milliseconds → "HH:mm:ss" cho nhãn trục X
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    /**
     * Broadcast listener — phải lưu reference để removeBroadcastListener() hoạt động đúng.
     * Không dùng lambda inline vì mỗi lần tạo là object khác nhau → remove được.
     */
    private final Consumer<Message> broadcastListener = this::handleBroadcast;

    // =========================================================================
    // KHỞI TẠO
    // =========================================================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Thiết lập biểu đồ
        bidSeries.setName("Giá đấu ($)");
        bidChart.getData().add(bidSeries);
        bidChart.setLegendVisible(false);

        // Đăng ký nhận broadcast realtime từ server
        ClientSocketManager.getInstance().addBroadcastListener(broadcastListener);
    }

    /**
     * Gọi từ MainDashboardController trước khi hiển thị màn hình.
     * Truyền item đã chọn, sau đó tải lịch sử giá và khởi động đếm ngược.
     */
    public void setAuctionItem(AuctionItem item) {
        this.currentItem   = item;
        this.currentItemId = item.getId();
        this.endTimeMs     = item.getEndTimeMs();

        // Cập nhật UI ban đầu
        lblName.setText(item.getName());
        lblDescription.setText(item.getDescription() != null ? item.getDescription() : "");
        lblPrice.setText(String.format("Giá hiện tại: $%.2f", item.getCurrentBid()));
        lblWinner.setText("Người dẫn đầu: " + item.getHighestBidder());

        // Khởi động đồng hồ đếm ngược
        startCountdown();

        // Tải lịch sử giá từ server (background thread để không đóng băng UI)
        loadBidHistory(currentItemId);
    }

    // =========================================================================
    // BIỂU ĐỒ — tải lịch sử và vẽ
    // =========================================================================

    /**
     * Gửi lệnh GET_BID_HISTORY tới server trên background thread,
     * parse JSON response rồi vẽ chart trên JavaFX thread.
     *
     * Response format: "SUCCESS:[{\"username\":\"...\",\"bidAmount\":100.0,\"bidTime\":1715...}, ...]"
     */
    private void loadBidHistory(int itemId) {
        new Thread(() -> {
            String response = ClientSocketManager.getInstance()
                    .sendRequest("GET_BID_HISTORY:" + itemId);

            Platform.runLater(() -> {
                if (response == null || response.startsWith("FAIL") ||
                        "TIMEOUT".equals(response) || "NOT_CONNECTED".equals(response)) {
                    lblChartStatus.setText("Không thể tải lịch sử giá.");
                    return;
                }

                // Cắt prefix "SUCCESS:"
                String json = response.startsWith("SUCCESS:") ? response.substring(8) : response;

                try {
                    // Parse thành List<Map<String, Object>>
                    Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
                    List<Map<String, Object>> history = gson.fromJson(json, listType);

                    // Xóa dữ liệu cũ (nếu có) rồi vẽ lại
                    bidSeries.getData().clear();

                    if (history == null || history.isEmpty()) {
                        lblChartStatus.setText("Chưa có lượt đặt giá nào.");
                        return;
                    }

                    for (Map<String, Object> entry : history) {
                        double amount    = ((Number) entry.get("bidAmount")).doubleValue();
                        long   timestamp = ((Number) entry.get("bidTime")).longValue();
                        String timeLabel = timeFormat.format(new Date(timestamp));
                        addChartPoint(timeLabel, amount);
                    }

                    lblChartStatus.setText(history.size() + " lượt đặt giá");

                } catch (Exception e) {
                    lblChartStatus.setText("Lỗi hiển thị biểu đồ.");
                    System.err.println("[DETAIL] Lỗi parse bid history: " + e.getMessage());
                }
            });
        }, "LoadBidHistoryThread").start();
    }

    /**
     * Thêm 1 điểm dữ liệu mới lên chart.
     * Nếu quá 60 điểm, bỏ điểm cũ nhất để chart không bị quá dày.
     * PHẢI gọi trên JavaFX thread.
     */
    private void addChartPoint(String timeLabel, double price) {
        bidSeries.getData().add(new XYChart.Data<>(timeLabel, price));

        // Giới hạn hiển thị 60 điểm gần nhất để chart dễ đọc
        if (bidSeries.getData().size() > 60) {
            bidSeries.getData().remove(0);
        }
    }

    // =========================================================================
    // ĐẶT GIÁ THỦ CÔNG
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

        final double finalBid = bidAmount;
        new Thread(() -> {
            String response = ClientSocketManager.getInstance()
                    .sendRequest("PLACE_BID:" + currentItemId + ":" + finalBid);

            Platform.runLater(() -> {
                if (response.startsWith("SUCCESS")) {
                    showSuccess(String.format("Đặt giá $%.2f thành công!", finalBid));
                    txtBidAmount.clear();
                    // Giá sẽ được cập nhật qua broadcast BID_UPDATE
                } else if ("TIMEOUT".equals(response) || "NOT_CONNECTED".equals(response)) {
                    showError("Lỗi kết nối. Kiểm tra lại server.");
                } else {
                    String reason = response.startsWith("FAIL:") ? response.substring(5) : response;
                    showError("Thất bại: " + reason);
                }
            });
        }, "PlaceBidThread").start();
    }

    // =========================================================================
    // AUTO-BIDDING
    // =========================================================================

    /**
     * Đăng ký auto-bid với server.
     * Format gửi: "AUTO_BID:<itemId>:<maxBid>:<increment>"
     */
    @FXML
    public void handleRegisterAutoBid(ActionEvent event) {
        String maxBidText   = txtMaxBid.getText().trim();
        String incrementText = txtIncrement.getText().trim();

        if (maxBidText.isEmpty() || incrementText.isEmpty()) {
            showAutoBidStatus("Vui lòng nhập đầy đủ maxBid và increment.", false);
            return;
        }

        double maxBid, increment;
        try {
            maxBid    = Double.parseDouble(maxBidText);
            increment = Double.parseDouble(incrementText);
        } catch (NumberFormatException e) {
            showAutoBidStatus("maxBid và increment phải là số hợp lệ.", false);
            return;
        }

        if (maxBid <= 0 || increment <= 0) {
            showAutoBidStatus("maxBid và increment phải lớn hơn 0.", false);
            return;
        }

        if (maxBid <= currentItem.getCurrentBid()) {
            showAutoBidStatus(
                    String.format("maxBid ($%.2f) phải cao hơn giá hiện tại ($%.2f).", maxBid, currentItem.getCurrentBid()),
                    false);
            return;
        }

        final double fMax = maxBid;
        final double fInc = increment;
        new Thread(() -> {
            // Lệnh: AUTO_BID:<itemId>:<maxBid>:<increment>
            String response = ClientSocketManager.getInstance()
                    .sendRequest("AUTO_BID:" + currentItemId + ":" + fMax + ":" + fInc);

            Platform.runLater(() -> {
                if (response.startsWith("SUCCESS")) {
                    showAutoBidStatus(
                            String.format("✅ Auto-bid đã đăng ký: max $%.2f, tăng $%.2f/lượt", fMax, fInc),
                            true);
                } else {
                    String reason = response.startsWith("FAIL:") ? response.substring(5) : response;
                    showAutoBidStatus("❌ " + reason, false);
                }
            });
        }, "AutoBidRegisterThread").start();
    }

    /**
     * Hủy auto-bid — gửi lệnh CANCEL_AUTO_BID tới server.
     */
    @FXML
    public void handleCancelAutoBid(ActionEvent event) {
        new Thread(() -> {
            String response = ClientSocketManager.getInstance()
                    .sendRequest("CANCEL_AUTO_BID:" + currentItemId);

            Platform.runLater(() -> {
                if (response.startsWith("SUCCESS")) {
                    showAutoBidStatus("Auto-bid đã hủy.", true);
                    txtMaxBid.clear();
                    txtIncrement.clear();
                } else {
                    String reason = response.startsWith("FAIL:") ? response.substring(5) : response;
                    showAutoBidStatus("Không thể hủy: " + reason, false);
                }
            });
        }, "CancelAutoBidThread").start();
    }

    // =========================================================================
    // XỬ LÝ BROADCAST TỪ SERVER
    // =========================================================================

    /** Nhận mọi broadcast, lọc ra broadcast liên quan đến item đang xem. */
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
                break;
        }
    }

    /**
     * Có bid mới → cập nhật giá, người dẫn đầu, VÀ thêm điểm mới lên biểu đồ.
     * Chạy trên JavaFX thread (đảm bảo bởi ClientSocketManager).
     */
    private void handleBidUpdate(String payload) {
        try {
            Item updated = gson.fromJson(payload, Item.class);
            if (updated.getId() != currentItemId) return;   // broadcast của item khác

            double newBid = updated.getCurrentHighestBid();
            String winner = updated.getCurrentHighestBidder();

            // Cập nhật state local
            currentItem.setCurrentBid(newBid);
            currentItem.setHighestBidder(winner);

            // Cập nhật labels
            lblPrice.setText(String.format("Giá hiện tại: $%.2f", newBid));
            lblWinner.setText("Người dẫn đầu: " + winner);

            // ── Thêm điểm MỚI lên biểu đồ (thời điểm NGAY BÂY GIỜ) ──
            String timeLabel = timeFormat.format(new Date(System.currentTimeMillis()));
            addChartPoint(timeLabel, newBid);
            lblChartStatus.setText(bidSeries.getData().size() + " lượt đặt giá");

            System.out.println("[DETAIL] Bid mới: $" + newBid + " bởi " + winner);

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
        lblMessage.setText(payload);
    }

    /** Anti-sniping → cập nhật endTime mới */
    private void handleTimeExtended(String payload) {
        try {
            TimeExtendedPayload ext = gson.fromJson(payload, TimeExtendedPayload.class);
            if (ext.itemId != currentItemId) return;
            endTimeMs = ext.newEndTime;
            System.out.println("[DETAIL] Phiên gia hạn. EndTime mới: " + endTimeMs);
        } catch (Exception e) {
            System.err.println("[DETAIL] Lỗi parse TIME_EXTENDED: " + e.getMessage());
        }
    }

    // =========================================================================
    // ĐỒNG HỒ ĐẾM NGƯỢC
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
        // Hủy listener và đồng hồ trước khi rời màn hình — tránh memory leak
        ClientSocketManager.getInstance().removeBroadcastListener(broadcastListener);
        if (countdownTimer != null) countdownTimer.stop();

        try {
            new NavigationUtils().switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
        } catch (Exception e) {
            e.printStackTrace();
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

    private void showAutoBidStatus(String msg, boolean success) {
        lblAutoBidStatus.setStyle(success
                ? "-fx-text-fill: #27ae60; -fx-font-style: italic;"
                : "-fx-text-fill: #e84118; -fx-font-style: italic;");
        lblAutoBidStatus.setText(msg);
    }

    // Inner classes để parse payload JSON
    private static class TimeExtendedPayload {
        int  itemId;
        long newEndTime;
    }
}