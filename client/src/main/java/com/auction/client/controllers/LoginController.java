package com.auction.client.controllers;

import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.event.ActionEvent;

public class LoginController {

    @FXML public TextField     txtUsername;
    @FXML public PasswordField txtPassword;
    @FXML public PasswordField txtConfirmPassword;
    @FXML public Button        btnLogin;

    private final NavigationUtils navUtils = new NavigationUtils();

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Lỗi", "Vui lòng nhập Username và Password!");
            return;
        }

        if (btnLogin != null) btnLogin.setDisable(true);

        Task<String> task = new Task<>() {
            @Override protected String call() {
                return ClientService.sendRequest("LOGIN:" + username + ":" + password);
            }
        };

        task.setOnSucceeded(e -> {
            if (btnLogin != null) btnLogin.setDisable(false);
            String response = task.getValue();
            System.out.println("[Login] Server response: " + response);

            if (response != null && response.startsWith("SUCCESS")) {
                String[] parts = response.split(":", 2);
                String role = (parts.length > 1 && !parts[1].isBlank()) ? parts[1].trim() : "BIDDER";
                ClientService.currentUsername = username;
                ClientService.currentRole     = role;
                System.out.println("[Login] Đăng nhập thành công: " + username + " (" + role + ")");
                try {
                    navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
                } catch (Exception ex) {
                    // Hiện lỗi thật ra màn hình thay vì im lặng
                    ex.printStackTrace();
                    showAlert(Alert.AlertType.ERROR, "Lỗi load giao diện",
                            "Không thể mở Dashboard:\n" + ex.getMessage()
                                    + (ex.getCause() != null ? "\nCause: " + ex.getCause().getMessage() : ""));
                }
            } else if ("CONNECTION_ERROR".equals(response)) {
                showAlert(Alert.AlertType.ERROR, "Lỗi kết nối",
                        "Không thể kết nối đến máy chủ.\nKiểm tra Server đã chạy chưa.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Sai thông tin",
                        "Username hoặc mật khẩu không đúng!\nServer trả về: " + response);
            }
        });

        task.setOnFailed(e -> {
            if (btnLogin != null) btnLogin.setDisable(false);
            Throwable ex = task.getException();
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Lỗi không xác định: " + ex.getMessage());
        });

        new Thread(task).start();
    }

    @FXML
    private void handleSignUp(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();
        String confirm  = txtConfirmPassword != null ? txtConfirmPassword.getText() : "";

        if (username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Lỗi", "Vui lòng nhập đầy đủ thông tin!");
            return;
        }
        if (!password.equals(confirm)) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Mật khẩu xác nhận không khớp!");
            return;
        }

        Task<String> task = new Task<>() {
            @Override protected String call() {
                return ClientService.sendRequest("REGISTER:" + username + ":" + password);
            }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (response != null && response.startsWith("SUCCESS")) {
                showAlert(Alert.AlertType.INFORMATION, "Thành công",
                        "Tạo tài khoản thành công!\nVui lòng đăng nhập.");
                handleGoToLogin(event);
            } else {
                showAlert(Alert.AlertType.ERROR, "Thất bại",
                        "Username đã tồn tại hoặc lỗi Server.\nServer trả về: " + response);
            }
        });

        task.setOnFailed(e -> {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Lỗi: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    @FXML
    private void handleGoToRegister(ActionEvent event) {
        try { navUtils.switchScene(event, "/RegisterView.fxml", "Create Account"); }
        catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi", e.getMessage());
        }
    }

    @FXML
    private void handleGoToLogin(ActionEvent event) {
        try { navUtils.switchScene(event, "/LoginView.fxml", "Login"); }
        catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi", e.getMessage());
        }
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