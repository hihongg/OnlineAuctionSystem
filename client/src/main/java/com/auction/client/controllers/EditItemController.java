package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Controller màn hình "Chỉnh sửa sản phẩm" (Seller / Admin).
 * Khớp với EditItem.fxml: lblItemId, lblError, txtName, txtDescription,
 * txtPrice, datePickerEnd, txtTimeEnd, btnSave.
 */
public class EditItemController {

    @FXML public TextField  txtName;
    @FXML public TextArea   txtDescription;
    @FXML public TextField  txtPrice;
    @FXML public DatePicker datePickerEnd;
    @FXML public TextField  txtTimeEnd;
    @FXML public Button     btnSave;
    @FXML public Label      lblItemId;
    @FXML public Label      lblError;

    private AuctionItem currentItem;
    private final NavigationUtils navUtils = new NavigationUtils();

    private static final int MIN_DURATION_MINUTES = 5;

    /** Gọi từ SellerItemsController sau khi loader.load() để truyền item cần sửa. */
    public void setItem(AuctionItem item) {
        this.currentItem = item;
        populateForm();
    }

    private void populateForm() {
        if (currentItem == null) return;

        txtName.setText(currentItem.getName());
        txtDescription.setText(currentItem.getDescription() != null ? currentItem.getDescription() : "");
        txtPrice.setText(String.valueOf(currentItem.getStartingPrice()));

        if (lblItemId != null) {
            lblItemId.setText("Sản phẩm #" + currentItem.getId()
                    + "  |  Loại: " + currentItem.getCategory()
                    + "  |  Trạng thái: " + currentItem.getStatus());
        }
        if (lblError != null) lblError.setText("");

        // Điền ngày giờ kết thúc hiện tại
        long endEpoch = currentItem.getEndTimeEpoch();
        if (endEpoch > 0) {
            LocalDateTime endDt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(endEpoch), ZoneId.systemDefault());
            datePickerEnd.setValue(endDt.toLocalDate());
            txtTimeEnd.setText(endDt.format(DateTimeFormatter.ofPattern("HH:mm")));
        }
    }

    @FXML
    public void handleSave(ActionEvent event) {
        if (lblError != null) lblError.setText("");

        // ── 1. Lấy dữ liệu ───────────────────────────────────────────────
        String name        = txtName.getText().trim();
        String description = txtDescription.getText().trim();
        String priceStr    = txtPrice.getText().trim();
        var    dateEnd     = datePickerEnd.getValue();
        String timeStrEnd  = txtTimeEnd.getText().trim();

        // ── 2. Validate ───────────────────────────────────────────────────
        if (name.isEmpty() || priceStr.isEmpty() || dateEnd == null || timeStrEnd.isEmpty()) {
            showError("Vui lòng nhập đầy đủ: tên, giá, ngày và giờ kết thúc!");
            return;
        }

        double startingPrice;
        try {
            startingPrice = Double.parseDouble(priceStr);
            if (startingPrice <= 0) { showError("Giá khởi điểm phải lớn hơn 0!"); return; }
        } catch (NumberFormatException e) {
            showError("Giá phải là số hợp lệ. Ví dụ: 500 hoặc 99.99");
            return;
        }

        LocalTime endLocalTime;
        try {
            endLocalTime = LocalTime.parse(timeStrEnd, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            showError("Giờ kết thúc phải đúng định dạng HH:mm. Ví dụ: 23:59");
            return;
        }

        LocalDateTime endDateTime = LocalDateTime.of(dateEnd, endLocalTime);
        LocalDateTime now         = LocalDateTime.now();

        if (!endDateTime.isAfter(now)) {
            showError("Thời gian kết thúc phải ở tương lai!");
            return;
        }
        if (endDateTime.isBefore(now.plusMinutes(MIN_DURATION_MINUTES))) {
            showError("Phiên đấu giá phải kéo dài ít nhất " + MIN_DURATION_MINUTES + " phút nữa.");
            return;
        }

        long endTimeMs = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // ── 3. Build JSON payload ─────────────────────────────────────────
        JsonObject payload = new JsonObject();
        payload.addProperty("itemId",        currentItem.getId());
        payload.addProperty("name",          name);
        payload.addProperty("description",   description);
        payload.addProperty("startingPrice", startingPrice);
        payload.addProperty("endTime",       endTimeMs);

        // ── 4. Gửi lên server (background thread) ────────────────────────
        if (btnSave != null) btnSave.setDisable(true);
        final ActionEvent finalEvent = event;

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return ClientService.sendRequest("UPDATE_ITEM:" + payload);
            }
        };

        task.setOnSucceeded(e -> {
            if (btnSave != null) btnSave.setDisable(false);
            String response = task.getValue();

            if (response == null || response.equals("CONNECTION_ERROR")) {
                showError("Không thể kết nối tới server. Kiểm tra server đang chạy.");
                return;
            }

            if (response.startsWith("SUCCESS")) {
                showAlert(Alert.AlertType.INFORMATION, "Thành công",
                        "Sản phẩm \"" + name + "\" đã được cập nhật!");
                try {
                    navUtils.switchScene(finalEvent, "/SellerItems.fxml", "Sản phẩm của tôi");
                } catch (Exception ex) { ex.printStackTrace(); }
            } else {
                String reason = response.startsWith("FAIL:") ? response.substring(5) : response;
                showError(reason);
            }
        });

        task.setOnFailed(e -> {
            if (btnSave != null) btnSave.setDisable(false);
            showError("Đã xảy ra lỗi không xác định.");
        });

        new Thread(task, "UpdateItemThread").start();
    }

    @FXML
    public void handleCancel(ActionEvent event) {
        try { navUtils.switchScene(event, "/SellerItems.fxml", "Sản phẩm của tôi"); }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    /** Hiển thị lỗi trên lblError (không dùng Alert popup để UX tốt hơn). */
    private void showError(String msg) {
        Platform.runLater(() -> {
            if (lblError != null) lblError.setText(msg);
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}