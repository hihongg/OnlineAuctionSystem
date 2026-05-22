package com.auction.client.controllers;

import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Controller màn hình "Đăng sản phẩm đấu giá" (Seller).
 *
 * Gửi ADD_ITEM lên server theo format JSON để tránh bug delimiter ':'.
 * Format: ADD_ITEM:{"name":"...","description":"...","startingPrice":100.0,"endTime":1748000000000,"category":"ELECTRONICS"}
 *
 * LƯU Ý cho thành viên phụ trách FXML (CreateItem.fxml):
 *   Khi sẵn sàng, hãy thêm 2 control vào form:
 *     1. TextArea   (fx:id="txtDescription") — Mô tả sản phẩm
 *     2. ComboBox   (fx:id="cmbCategory")    — Items: ELECTRONICS, ART, VEHICLE
 *   Cho đến lúc đó, description = "" và category = "ELECTRONICS" được dùng mặc định.
 */
public class CreateItemController {

    // ------------------------------------------------------------------
    // FXML fields có sẵn trong CreateItem.fxml
    // ------------------------------------------------------------------
    @FXML private TextField  txtName;
    @FXML private TextField  txtPrice;
    @FXML private DatePicker datePickerEnd;
    @FXML private TextField  txtTimeEnd;

    // ------------------------------------------------------------------
    // FXML fields CHỜ thành viên FXML thêm vào — KHÔNG dùng @FXML để
    // tránh warning "never assigned". Sẽ luôn là null cho đến khi FXML
    // được cập nhật; code bên dưới xử lý null-safe.
    // ------------------------------------------------------------------
    private TextArea      txtDescription; // sẽ được inject sau khi FXML có fx:id="txtDescription"
    private ComboBox<String> cmbCategory; // sẽ được inject sau khi FXML có fx:id="cmbCategory"

    private final NavigationUtils navUtils      = new NavigationUtils();
    private final ClientService   clientService = new ClientService();

    // ------------------------------------------------------------------
    // Khởi tạo giá trị mặc định (chạy sau khi FXML load xong)
    // ------------------------------------------------------------------
    @FXML
    public void initialize() {
        // Chỉ setup khi FXML đã có ComboBox
        if (cmbCategory != null) {
            cmbCategory.getItems().addAll("ELECTRONICS", "ART", "VEHICLE");
            cmbCategory.setValue("ELECTRONICS");
        }
    }

    // ------------------------------------------------------------------
    // Xử lý khi bấm "Xác nhận Đăng bán"
    // ------------------------------------------------------------------
    @FXML
    public void handleCreate(ActionEvent event) {
        // 1. Lấy dữ liệu từ form
        String name        = txtName.getText().trim();
        String description = (txtDescription != null) ? txtDescription.getText().trim() : "";
        String priceStr    = txtPrice.getText().trim();
        var    date        = datePickerEnd.getValue();
        String timeStr     = txtTimeEnd.getText().trim();
        String category    = (cmbCategory != null && cmbCategory.getValue() != null)
                ? cmbCategory.getValue()
                : "ELECTRONICS";

        // 2. Validate cơ bản
        if (name.isEmpty() || priceStr.isEmpty() || date == null || timeStr.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        double startingPrice;
        try {
            startingPrice = Double.parseDouble(priceStr);
            if (startingPrice <= 0) {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Giá khởi điểm phải lớn hơn 0!");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi định dạng", "Giá phải là số hợp lệ (VD: 500 hoặc 99.99)");
            return;
        }

        LocalTime endLocalTime;
        try {
            endLocalTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi định dạng", "Giờ phải đúng định dạng HH:mm (VD: 23:59)");
            return;
        }

        LocalDateTime endDateTime = LocalDateTime.of(date, endLocalTime);
        if (endDateTime.isBefore(LocalDateTime.now())) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Thời gian kết thúc phải ở tương lai!");
            return;
        }

        // 3. Chuyển LocalDateTime → milliseconds
        long endTimeMs = endDateTime
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        // 4. Tạo JSON payload — escape ký tự đặc biệt để JSON không bị vỡ
        String jsonPayload = String.format(
                "{\"name\":\"%s\",\"description\":\"%s\",\"startingPrice\":%.2f,\"endTime\":%d,\"category\":\"%s\"}",
                escapeJson(name),
                escapeJson(description),
                startingPrice,
                endTimeMs,
                category
        );

        // 5. Gửi lên server và xử lý response
        String response = clientService.sendRequest("ADD_ITEM:" + jsonPayload);

        if (response == null || response.equals("CONNECTION_ERROR")) {
            showAlert(Alert.AlertType.ERROR, "Lỗi kết nối", "Không thể kết nối tới máy chủ. Thử lại sau.");
            return;
        }

        if (response.startsWith("SUCCESS")) {
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Sản phẩm đã được đăng bán!");
            // FIX lỗi "Unhandled exception": switchScene() ném checked Exception
            // → phải bọc trong try-catch.
            try {
                navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi điều hướng",
                        "Không thể chuyển màn hình: " + e.getMessage());
            }
        } else {
            String reason = response.startsWith("FAIL:") ? response.substring(5) : response;
            showAlert(Alert.AlertType.ERROR, "Không thể đăng bán", reason);
        }
    }

    // ------------------------------------------------------------------
    // Xử lý khi bấm "Quay lại"
    // ------------------------------------------------------------------
    @FXML
    public void handleCancel(ActionEvent event) {
        try {
            navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi điều hướng",
                    "Không thể chuyển màn hình: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // HELPER: escape ký tự đặc biệt trong JSON string value
    // ------------------------------------------------------------------
    private String escapeJson(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ------------------------------------------------------------------
    // HELPER: hiển thị Alert
    // ------------------------------------------------------------------
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}