package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import java.io.File;
import java.lang.reflect.Type;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Controller màn hình Chi tiết sản phẩm.
 *
 * 3 TÍNH NĂNG MỚI:
 *   1. Đặt giá thật — gọi PLACE_BID lên server, xử lý SUCCESS/FAIL
 *   2. Realtime update — lắng nghe BID_UPDATE / AUCTION_ENDED / TIME_EXTENDED từ server
 *   3. Bid History Chart — LineChart giá theo thời gian, cập nhật live
 *
 * FIX: loadProductImage() giờ hỗ trợ load file ảnh ngoài ổ đĩa (uploads/).
 */
public class ItemDetailController {

    // ── FXML fields ──────────────────────────────────────────────────────────
    @FXML public Label      lblName;
    @FXML public Label      lblPrice;
    @FXML public Label      lblBidder;
    @FXML public Label      lblTime;
    @FXML public Label      lblStatus;
    @FXML public Label      lblMessage;
    @FXML public TextField  txtBidAmount;
    @FXML public Button     btnPlaceBid;
    @FXML public ImageView  imgProduct;
    @FXML public TextArea   txtDescription;
    @FXML public LineChart<String, Number> bidChart;

    // ── State ────────────────────────────────────────────────────────────────
    private AuctionItem currentItem;
    private Timeline    countdownTimeline;

    private XYChart.Series<String, Number> bidSeries;
    private int bidIndex = 0;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Gson GSON = new Gson();

    private Consumer<String> pushListener;

    // =========================================================================
    // KHỞI TẠO — được gọi từ MainDashboardController sau khi load FXML
    // =========================================================================
    public void setAuctionItem(AuctionItem item) {
        this.currentItem = item;

        lblName.setText(item.getName());
        updatePriceUI(item.getCurrentBid(), item.getCurrentHighestBidder());
        updateStatusUI(item.getStatus());

        if (txtDescription != null) {
            txtDescription.setText(item.getDescription() != null ? item.getDescription() : "");
            txtDescription.setEditable(false);
            txtDescription.setWrapText(true);
        }

        loadProductImage(item);
        setupCountdownTimer(item);
        setupBidChart();

        if (item.getId() > 0) {
            watchItem(item.getId());
            loadBidHistory(item.getId());
        }
    }

    // =========================================================================
    // NÚT ĐẶT GIÁ
    // =========================================================================
    @FXML
    public void handlePlaceBid(ActionEvent event) {
        if (currentItem == null) return;

        if (!currentItem.isRunning()) {
            showMessage("Phiên đấu giá này đã kết thúc.", false);
            return;
        }

        double bidAmount;
        try {
            bidAmount = Double.parseDouble(txtBidAmount.getText().trim());
        } catch (NumberFormatException e) {
            showMessage("Lỗi: Vui lòng nhập số tiền hợp lệ.", false);
            return;
        }

        if (bidAmount <= currentItem.getCurrentBid()) {
            showMessage(String.format("Lỗi: Giá đặt phải cao hơn giá hiện tại ($%.2f).",
                    currentItem.getCurrentBid()), false);
            return;
        }

        btnPlaceBid.setDisable(true);
        showMessage("Đang gửi yêu cầu...", true);

        final double finalBid = bidAmount;
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return ClientService.sendRequest(
                        "PLACE_BID:" + currentItem.getId() + ":" + finalBid);
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            btnPlaceBid.setDisable(false);

            if (response != null && response.startsWith("SUCCESS")) {
                showMessage("✅ Đặt giá $" + String.format("%.2f", finalBid) + " thành công!", true);
                txtBidAmount.clear();
            } else if ("CONNECTION_ERROR".equals(response)) {
                showMessage("❌ Lỗi kết nối. Thử lại sau.", false);
            } else {
                String reason = (response != null && response.startsWith("FAIL:"))
                        ? response.substring(5) : "Đặt giá thất bại.";
                showMessage("❌ " + reason, false);
            }
        });

        task.setOnFailed(e -> {
            btnPlaceBid.setDisable(false);
            showMessage("❌ Lỗi không xác định. Thử lại.", false);
        });

        Thread t = new Thread(task, "PlaceBidThread");
        t.setDaemon(true);
        t.start();
    }

    // =========================================================================
    // WATCH_ITEM + PUSH LISTENER (Realtime update)
    // =========================================================================
    private void watchItem(int itemId) {
        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                ClientService.sendRequest("WATCH_ITEM:" + itemId);
                return null;
            }
        };
        new Thread(task, "WatchItemThread").start();

        pushListener = (String msg) -> {
            Platform.runLater(() -> handlePushMessage(msg));
        };
        ClientService.addPushListener(pushListener);
    }

    private void handlePushMessage(String msg) {
        if (currentItem == null) return;

        // BUG FIX: Server gửi push message dưới 2 dạng:
        //   - Plain text: "BID_UPDATE:{...}"  (từ các lệnh trực tiếp)
        //   - JSON:       {"action":"BID_UPDATE","payload":"{...}"}  (từ broadcastToItemWatchers)
        // Chuẩn hóa về plain-text trước khi xử lý.
        String action  = null;
        String payload = null;

        if (msg.startsWith("BID_UPDATE:")) {
            action  = "BID_UPDATE";
            payload = msg.substring("BID_UPDATE:".length());
        } else if (msg.startsWith("AUCTION_ENDED:")) {
            action  = "AUCTION_ENDED";
            payload = msg.substring("AUCTION_ENDED:".length());
        } else if (msg.startsWith("TIME_EXTENDED:")) {
            action  = "TIME_EXTENDED";
            payload = msg.substring("TIME_EXTENDED:".length());
        } else if (msg.startsWith("{")) {
            // JSON format từ Message.toJson() — parse để lấy action + payload
            try {
                com.auction.shared.models.Message parsed =
                        com.auction.shared.models.Message.fromJson(msg);
                action  = parsed.getAction();
                payload = parsed.getPayload();
            } catch (Exception e) {
                System.err.println("[Detail] Không parse được push message: " + e.getMessage());
                return;
            }
        }

        if (action == null) return;

        if ("BID_UPDATE".equalsIgnoreCase(action)) {
            try {
                Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> updatedItem = GSON.fromJson(payload, mapType);

                Number idFromServer = (Number) updatedItem.get("id");
                if (idFromServer == null || idFromServer.intValue() != currentItem.getId()) return;

                Number newBid    = (Number) updatedItem.get("currentHighestBid");
                String newBidder = (String) updatedItem.get("currentHighestBidder");
                String newStatus = (String) updatedItem.get("status");

                if (newBid != null)    currentItem.setCurrentHighestBid(newBid.doubleValue());
                if (newBidder != null) currentItem.setCurrentHighestBidder(newBidder);
                if (newStatus != null) currentItem.setStatus(newStatus);

                if (newBid != null) {
                    updatePriceUI(newBid.doubleValue(), newBidder);
                    addChartPoint(newBid.doubleValue());
                }
                updateStatusUI(newStatus);

            } catch (Exception e) {
                System.err.println("[Detail] Lỗi parse BID_UPDATE: " + e.getMessage());
            }

        } else if ("AUCTION_ENDED".equalsIgnoreCase(action)) {
            currentItem.setStatus("FINISHED");
            updateStatusUI("FINISHED");
            disableBidControls();
            showMessage("🏁 " + (payload != null ? payload : "Phiên đấu giá đã kết thúc."), true);

        } else if ("TIME_EXTENDED".equalsIgnoreCase(action)) {
            try {
                Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> data = GSON.fromJson(payload, mapType);
                Number newEndTime    = (Number) data.get("newEndTime");
                Number payloadItemId = (Number) data.get("itemId");

                if (newEndTime != null && payloadItemId != null
                        && payloadItemId.intValue() == currentItem.getId()) {
                    currentItem.setEndTime(newEndTime.longValue());
                    showMessage("⏰ Phiên được gia hạn thêm 5 phút!", true);
                }
            } catch (Exception e) {
                System.err.println("[Detail] Lỗi parse TIME_EXTENDED: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // BID HISTORY CHART
    // =========================================================================
    private void setupBidChart() {
        if (bidChart == null) return;
        bidSeries = new XYChart.Series<>();
        bidSeries.setName("Giá đấu ($)");
        bidChart.getData().add(bidSeries);
        bidChart.setAnimated(false);
        bidChart.setCreateSymbols(true);
        bidChart.getXAxis().setLabel("Thời gian");
        bidChart.getYAxis().setLabel("Giá ($)");
    }

    private void loadBidHistory(int itemId) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return ClientService.sendRequest("GET_BID_HISTORY:" + itemId);
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (response == null || !response.startsWith("SUCCESS:")) return;
            String json = response.substring("SUCCESS:".length());

            try {
                Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
                List<Map<String, Object>> history = GSON.fromJson(json, listType);
                if (history == null || history.isEmpty()) return;

                for (Map<String, Object> entry : history) {
                    Number bidAmount = (Number) entry.get("bidAmount");
                    Number bidTime   = (Number) entry.get("bidTime");

                    if (bidAmount == null) continue;
                    String timeLabel = formatEpochToTime(bidTime != null ? bidTime.longValue() : 0);
                    addChartPointLabeled(bidAmount.doubleValue(), timeLabel);
                }
                bidIndex = history.size();

            } catch (Exception ex) {
                System.err.println("[Detail] Lỗi parse bid history: " + ex.getMessage());
            }
        });

        Thread t = new Thread(task, "LoadHistoryThread");
        t.setDaemon(true);
        t.start();
    }

    private void addChartPoint(double price) {
        if (bidSeries == null) return;
        String label = LocalDateTime.now().format(TIME_FMT);
        addChartPointLabeled(price, label);
    }

    private void addChartPointLabeled(double price, String timeLabel) {
        if (bidSeries == null) return;
        bidSeries.getData().add(new XYChart.Data<>(timeLabel, price));
    }

    private String formatEpochToTime(long epochMs) {
        if (epochMs <= 0) return "Bid #" + (++bidIndex);
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                .format(TIME_FMT);
    }

    // =========================================================================
    // ĐẾM NGƯỢC THỜI GIAN
    // =========================================================================
    private void setupCountdownTimer(AuctionItem item) {
        if (countdownTimeline != null) countdownTimeline.stop();

        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            long remaining = currentItem.getEndTimeEpoch() - System.currentTimeMillis();
            if (remaining <= 0) {
                lblTime.setText("⏱ Đã kết thúc");
                disableBidControls();
                countdownTimeline.stop();
            } else {
                long hours   = remaining / 3_600_000;
                long minutes = (remaining % 3_600_000) / 60_000;
                long seconds = (remaining % 60_000) / 1_000;
                lblTime.setText(String.format("⏱ Còn lại: %02d:%02d:%02d", hours, minutes, seconds));
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    // =========================================================================
    // QUAY LẠI DASHBOARD
    // =========================================================================
    @FXML
    public void handleBackToDashboard(ActionEvent event) {
        stopWatching();
        try {
            new NavigationUtils().switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopWatching() {
        if (countdownTimeline != null) countdownTimeline.stop();

        if (pushListener != null) {
            ClientService.removePushListener(pushListener);
            pushListener = null;
        }

        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                ClientService.sendRequest("UNWATCH_ITEM");
                return null;
            }
        };
        Thread t = new Thread(task, "UnwatchThread");
        t.setDaemon(true);
        t.start();
    }

    // =========================================================================
    // HELPER — cập nhật UI
    // =========================================================================
    private void updatePriceUI(double price, String bidder) {
        lblPrice.setText(String.format("Giá hiện tại: $%.2f", price));
        if (lblBidder != null) {
            String displayBidder = (bidder == null || bidder.isBlank()) ? "Chưa có" : bidder;
            lblBidder.setText("Người dẫn đầu: " + displayBidder);
        }
    }

    private void updateStatusUI(String status) {
        if (lblStatus == null || status == null) return;
        switch (status) {
            case "RUNNING"  -> { lblStatus.setText("🟢 Đang diễn ra"); lblStatus.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;"); }
            case "FINISHED",
                 "PAID"     -> { lblStatus.setText("🔴 Đã kết thúc");   lblStatus.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;"); disableBidControls(); }
            case "CANCELED" -> { lblStatus.setText("⛔ Đã hủy");        lblStatus.setStyle("-fx-text-fill: #95a5a6; -fx-font-weight: bold;"); disableBidControls(); }
            default         -> lblStatus.setText(status);
        }
    }

    private void disableBidControls() {
        if (txtBidAmount != null) txtBidAmount.setDisable(true);
        if (btnPlaceBid  != null) btnPlaceBid.setDisable(true);
    }

    private void showMessage(String msg, boolean isSuccess) {
        if (lblMessage == null) return;
        lblMessage.setText(msg);
        lblMessage.setStyle(isSuccess
                ? "-fx-text-fill: #27ae60; -fx-font-style: normal; -fx-font-weight: bold;"
                : "-fx-text-fill: #e74c3c; -fx-font-style: italic;");
    }

    /**
     * Load ảnh sản phẩm cho màn hình chi tiết.
     *
     * FIX: Trước đây chỉ dùng getClass().getResource() nên không tìm được
     * file ảnh ngoài ổ đĩa (thư mục uploads/).
     * Giờ thử load từ File system trước, sau mới fallback về classpath.
     */
    private void loadProductImage(AuctionItem item) {
        if (imgProduct == null) return;

        String path = item.getImagePath();
        if (path == null || path.isBlank()) return;

        // Thử load file thật từ ổ đĩa (uploads/xxx.png)
        try {
            File imageFile = new File(path);
            if (imageFile.exists()) {
                imgProduct.setImage(new Image(imageFile.toURI().toString(), true));
                return;
            }
        } catch (Exception e) {
            System.out.println("[Detail] Không load được file ảnh: " + path + " — " + e.getMessage());
        }

        // Thử load từ URL (http://) hoặc classpath
        try {
            URL url = path.startsWith("http") ? new URL(path) : getClass().getResource(path);
            if (url != null) {
                imgProduct.setImage(new Image(url.toExternalForm(), true));
            }
        } catch (Exception e) {
            System.out.println("[Detail] Không tải được ảnh: " + e.getMessage());
        }
    }
}