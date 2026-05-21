package com.auction.client.controllers;

import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.event.ActionEvent;

public class LoginController {

    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPassword;

    // Biến này có thể null nếu đang ở màn hình Login, nên không bắt buộc (không lỗi)
    @FXML private PasswordField txtConfirmPassword;

    private ClientService clientService = new ClientService();
    private NavigationUtils navUtils = new NavigationUtils();

    // 1. XỬ LÝ KHI BẤM NÚT LOGIN (Ở màn hình Login)
    @FXML
    private void handleLogin(ActionEvent event) {
        String email = txtEmail.getText().trim();
        String password = txtPassword.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Lỗi", "Vui lòng nhập Email và Password!");
            return;
        }

        // Gửi yêu cầu LOGIN lên Server
        String requestMessage = "LOGIN:" + email + ":" + password;
        String response = clientService.sendRequest(requestMessage);

        // Xử lý phản hồi từ Server
        if ("SUCCESS".equals(response)) {
            try {
                navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if ("CONNECTION_ERROR".equals(response)) {
            showAlert(Alert.AlertType.ERROR, "Lỗi kết nối", "Không thể kết nối đến máy chủ. Vui lòng thử lại sau.");
        } else {
            // Nếu Server trả về "FAIL" hoặc bất kỳ thứ gì khác
            showAlert(Alert.AlertType.ERROR, "Sai thông tin", "Email hoặc mật khẩu không đúng!");
        }
    }

    // 2. XỬ LÝ KHI BẤM NÚT SIGN UP (Ở màn hình Register)
    @FXML
    private void handleSignUp(ActionEvent event) {
        String email = txtEmail.getText().trim();
        String password = txtPassword.getText();
        String confirm = txtConfirmPassword.getText();

        if (email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Lỗi", "Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        if (!password.equals(confirm)) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Mật khẩu xác nhận không khớp!");
            return;
        }

        String response = clientService.sendRequest("REGISTER:" + email + ":" + password);
        if ("SUCCESS".equals(response)) {
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Tạo tài khoản thành công! Vui lòng đăng nhập.");
            handleGoToLogin(event); // Trở về trang login
        } else {
            showAlert(Alert.AlertType.ERROR, "Thất bại", "Email đã tồn tại hoặc lỗi Server");
        }
    }

    // 3. ĐIỀU HƯỚNG TỪ LOGIN -> REGISTER
    @FXML
    private void handleGoToRegister(ActionEvent event) {
        try {
            navUtils.switchScene(event, "/RegisterView.fxml", "Create Account");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 4. ĐIỀU HƯỚNG TỪ REGISTER -> LOGIN
    @FXML
    private void handleGoToLogin(ActionEvent event) {
        try {
            navUtils.switchScene(event, "/LoginView.fxml", "Login");
        } catch (Exception e) {
            e.printStackTrace();
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