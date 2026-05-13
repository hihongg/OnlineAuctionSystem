package com.auction.client.controllers;

import com.auction.client.utils.NavigationUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class CreateItemController {

    @FXML private TextField txtName;
    @FXML private TextField txtPrice;
    @FXML private DatePicker datePickerEnd;
    @FXML private TextField txtTimeEnd;

    private NavigationUtils navUtils = new NavigationUtils();

    // Xử lý khi bấm nút "Xác nhận Đăng bán"
    @FXML
    public void handleCreate(ActionEvent event) {
        try {
            // 1. Lấy dữ liệu từ Form
            String name = txtName.getText();
            String priceStr = txtPrice.getText();
            var date = datePickerEnd.getValue();
            String timeStr = txtTimeEnd.getText();

            // 2. Kiểm tra dữ liệu trống (Validation cơ bản)
            if (name.isEmpty() || priceStr.isEmpty() || date == null || timeStr.isEmpty()) {
                showAlert("Lỗi", "Vui lòng nhập đầy đủ thông tin!");
                return;
            }

            // 3. Xử lý logic ghép Ngày và Giờ
            LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalDateTime endTime = LocalDateTime.of(date, time);

            if (endTime.isBefore(LocalDateTime.now())) {
                showAlert("Lỗi", "Thời gian kết thúc phải ở tương lai!");
                return;
            }

            // --- TẠM THỜI: In ra console để kiểm tra (Bước sau sẽ gửi lên Server) ---
            System.out.println("Đang tạo sản phẩm mới: " + name + " với giá khởi điểm: $" + priceStr);
            System.out.println("Thời gian kết thúc: " + endTime);

            // 4. Quay trở về Dashboard sau khi đăng thành công
            navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");

        } catch (Exception e) {
            showAlert("Lỗi định dạng", "Giá phải là số và Giờ phải đúng định dạng HH:mm (VD: 23:59)");
        }
    }

    // Xử lý khi bấm nút "Quay lại"
    @FXML
    public void handleCancel(ActionEvent event) {
        try {
            navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}