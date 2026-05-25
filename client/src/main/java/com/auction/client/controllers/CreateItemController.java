package com.auction.client.controllers;

import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Controller màn hình "Đăng sản phẩm đấu giá" (Seller / Admin).
 *
 * FIX 1 — JSON locale bug:
 *   String.format("%.2f", 500.0) trên Windows tiếng Việt → "500,00" (dấu phẩy)
 *   → Gson ném JsonSyntaxException khi parse.
 *   Giải pháp: dùng Gson JsonObject để build JSON — luôn dùng dấu chấm chuẩn RFC 8259.
 *
 * FIX 2 — Race condition / item biến mất:
 *   Nếu end_time quá gần hiện tại (vd: còn 2 giây), AuctionService scheduler (1s)
 *   sẽ activate OPEN→RUNNING rồi ngay lập tức close RUNNING→FINISHED trước khi
 *   dashboard kịp gọi GET_ITEMS. Item bị FINISHED không hiện lên dashboard.
 *   Giải pháp: bắt buộc end_time tối thiểu 5 phút từ bây giờ.
 *
 * THÊM MỚI — Image upload:
 *   Người dùng chọn file ảnh (JPG/PNG/GIF) từ máy tính.
 *   File được encode Base64 và gửi kèm JSON payload lên server.
 *   Server giải mã và lưu vào thư mục uploads/, trả về imagePath.
 *   Giới hạn: tối đa 5MB để tránh quá tải socket.
 */
public class CreateItemController {

    @FXML public TextField        txtName;
    @FXML public TextField        txtPrice;
    @FXML public DatePicker       datePickerEnd;
    @FXML public TextField        txtTimeEnd;
    @FXML public TextArea         txtDescription;
    @FXML public ComboBox<String> cmbCategory;
    @FXML public Button           btnConfirm;

    // --- Image fields (MỚI) ---
    @FXML public StackPane  imagePreviewPane;
    @FXML public ImageView  imgPreview;
    @FXML public Label      lblImagePlaceholder;
    @FXML public Label      lblImageName;
    @FXML public Button     btnClearImage;

    /** File ảnh người dùng đã chọn (null nếu chưa chọn) */
    private File selectedImageFile = null;

    private final NavigationUtils navUtils = new NavigationUtils();

    // Thời gian tối thiểu (phút) từ lúc đăng bán đến khi kết thúc
    private static final int MIN_DURATION_MINUTES = 5;
    // Giới hạn kích thước ảnh: 5 MB
    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;

    @FXML
    public void initialize() {
        if (cmbCategory != null) {
            cmbCategory.getItems().addAll("ELECTRONICS", "ART", "VEHICLE");
            cmbCategory.setValue("ELECTRONICS");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  XỬ LÝ ẢNH
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Mở FileChooser để người dùng chọn ảnh sản phẩm.
     * Hỗ trợ JPG, JPEG, PNG, GIF.
     */
    @FXML
    public void handleChooseImage(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh sản phẩm");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Ảnh (JPG, PNG, GIF)", "*.jpg", "*.jpeg", "*.png", "*.gif"),
                new FileChooser.ExtensionFilter("Tất cả file", "*.*")
        );

        // Lấy Window từ bất kỳ node nào đang có
        Window window = btnConfirm.getScene().getWindow();
        File file = fileChooser.showOpenDialog(window);

        if (file != null) {
            // Kiểm tra kích thước tối đa
            if (file.length() > MAX_IMAGE_SIZE_BYTES) {
                showAlert(Alert.AlertType.ERROR, "Ảnh quá lớn",
                        "Ảnh không được vượt quá 5MB. File bạn chọn: "
                                + String.format("%.2f", file.length() / (1024.0 * 1024.0)) + " MB");
                return;
            }

            selectedImageFile = file;

            // Hiển thị preview
            try {
                Image image = new Image(file.toURI().toString());
                imgPreview.setImage(image);
                imgPreview.setVisible(true);
                lblImagePlaceholder.setVisible(false);
                lblImageName.setText("✔ " + file.getName());
                btnClearImage.setVisible(true);
            } catch (Exception e) {
                showAlert(Alert.AlertType.WARNING, "Không thể xem trước",
                        "Đã chọn file nhưng không thể hiển thị preview: " + e.getMessage());
            }
        }
    }

    /**
     * Xóa ảnh đã chọn, quay về trạng thái ban đầu.
     */
    @FXML
    public void handleClearImage(ActionEvent event) {
        selectedImageFile = null;
        imgPreview.setImage(null);
        imgPreview.setVisible(false);
        lblImagePlaceholder.setVisible(true);
        lblImageName.setText("");
        btnClearImage.setVisible(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GỬI FORM
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void handleCreate(ActionEvent event) {
        // ── 1. Lấy dữ liệu từ form ────────────────────────────────────────
        String name        = txtName.getText().trim();
        String description = (txtDescription != null) ? txtDescription.getText().trim() : "";
        String priceStr    = txtPrice.getText().trim();
        var    date        = datePickerEnd.getValue();
        String timeStr     = (txtTimeEnd != null) ? txtTimeEnd.getText().trim() : "";
        String category    = (cmbCategory != null && cmbCategory.getValue() != null)
                ? cmbCategory.getValue() : "ELECTRONICS";

        // ── 2. Validate: trường bắt buộc ──────────────────────────────────
        if (name.isEmpty() || priceStr.isEmpty() || date == null || timeStr.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Thiếu thông tin",
                    "Vui lòng nhập đầy đủ: tên, giá, ngày và giờ kết thúc!");
            return;
        }

        // ── 3. Validate: giá ──────────────────────────────────────────────
        double startingPrice;
        try {
            startingPrice = Double.parseDouble(priceStr);
            if (startingPrice <= 0) {
                showAlert(Alert.AlertType.ERROR, "Giá không hợp lệ",
                        "Giá khởi điểm phải lớn hơn 0!");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Giá không hợp lệ",
                    "Giá phải là số hợp lệ. Ví dụ: 500 hoặc 99.99");
            return;
        }

        // ── 4. Validate: định dạng giờ ────────────────────────────────────
        LocalTime endLocalTime;
        try {
            endLocalTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Giờ không hợp lệ",
                    "Giờ phải đúng định dạng HH:mm. Ví dụ: 23:59");
            return;
        }

        // ── 5. Validate: thời gian kết thúc phải ở tương lai ─────────────
        LocalDateTime endDateTime = LocalDateTime.of(date, endLocalTime);
        LocalDateTime now = LocalDateTime.now();

        if (!endDateTime.isAfter(now)) {
            showAlert(Alert.AlertType.ERROR, "Thời gian không hợp lệ",
                    "Thời gian kết thúc phải ở tương lai!");
            return;
        }

        // ── 6. FIX 2: Validate thời gian tối thiểu ───────────────────────
        if (endDateTime.isBefore(now.plusMinutes(MIN_DURATION_MINUTES))) {
            showAlert(Alert.AlertType.ERROR, "Thời gian quá ngắn",
                    "Phiên đấu giá phải kéo dài ít nhất " + MIN_DURATION_MINUTES
                            + " phút kể từ bây giờ.\n"
                            + "Hãy chọn thời gian kết thúc muộn hơn.");
            return;
        }

        // ── 7. Chuyển LocalDateTime → epoch milliseconds ──────────────────
        long endTimeMs = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // ── 8. Encode ảnh sang Base64 (nếu có) ───────────────────────────
        String imageBase64 = null;
        String imageExtension = null;
        if (selectedImageFile != null) {
            try {
                byte[] imageBytes = Files.readAllBytes(selectedImageFile.toPath());
                imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
                // Lấy phần mở rộng file (jpg, png, gif)
                String fileName = selectedImageFile.getName().toLowerCase();
                imageExtension = fileName.substring(fileName.lastIndexOf('.') + 1);
                if (imageExtension.equals("jpeg")) imageExtension = "jpg";
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi đọc ảnh",
                        "Không thể đọc file ảnh: " + e.getMessage());
                return;
            }
        }

        // ── 9. FIX 1: Build JSON bằng Gson ───────────────────────────────
        JsonObject payload = new JsonObject();
        payload.addProperty("name",          name);
        payload.addProperty("description",   description);
        payload.addProperty("startingPrice", startingPrice);
        payload.addProperty("endTime",       endTimeMs);
        payload.addProperty("category",      category);

        // Thêm ảnh vào payload nếu có
        if (imageBase64 != null) {
            payload.addProperty("imageBase64",  imageBase64);
            payload.addProperty("imageExt",     imageExtension);
        }

        String jsonPayload = payload.toString();

        // ── 10. Gửi lên server (trên background thread) ───────────────────
        if (btnConfirm != null) btnConfirm.setDisable(true);

        final String finalPayload = jsonPayload;
        final ActionEvent finalEvent = event;

        Task<String> sendTask = new Task<>() {
            @Override
            protected String call() {
                return ClientService.sendRequest("ADD_ITEM:" + finalPayload);
            }
        };

        sendTask.setOnSucceeded(e -> {
            if (btnConfirm != null) btnConfirm.setDisable(false);
            String response = sendTask.getValue();

            if (response == null || response.equals("CONNECTION_ERROR")) {
                showAlert(Alert.AlertType.ERROR, "Lỗi kết nối",
                        "Không thể kết nối tới máy chủ. Kiểm tra server đang chạy rồi thử lại.");
                return;
            }

            if (response.startsWith("SUCCESS")) {
                showAlert(Alert.AlertType.INFORMATION, "Thành công",
                        "Sản phẩm \"" + name + "\" đã được đăng bán thành công!");
                try {
                    navUtils.switchScene(finalEvent, "/MainDashboard.fxml", "Auction Dashboard");
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Lỗi điều hướng",
                            "Không thể chuyển màn hình: " + ex.getMessage());
                }
            } else {
                String reason = response.startsWith("FAIL:") ? response.substring(5) : response;
                showAlert(Alert.AlertType.ERROR, "Không thể đăng bán", reason);
            }
        });

        sendTask.setOnFailed(e -> {
            if (btnConfirm != null) btnConfirm.setDisable(false);
            Throwable ex = sendTask.getException();
            showAlert(Alert.AlertType.ERROR, "Lỗi",
                    "Đã xảy ra lỗi: " + (ex != null ? ex.getMessage() : "unknown"));
        });

        Thread t = new Thread(sendTask, "AddItemThread");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleCancel(ActionEvent event) {
        try {
            navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi điều hướng",
                    "Không thể chuyển màn hình: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}