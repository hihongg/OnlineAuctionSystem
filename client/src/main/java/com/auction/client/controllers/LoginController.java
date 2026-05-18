package com.auction.client.controllers;

import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.VBox;

public class LoginController {

    @FXML private Label lblTitle;
    @FXML private VBox loginForm;
    @FXML private VBox registerForm;

    // Login fields
    @FXML private TextField txtLoginUsername;
    @FXML private PasswordField txtLoginPassword;

    // Register fields
    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPassword;

    private boolean isLoginMode = true;
    private ClientService clientService = new ClientService();
    private NavigationUtils navUtils = new NavigationUtils();

    // Xử lý Login
    @FXML
    private void handleLogin(ActionEvent event) {
        String username = txtLoginUsername.getText().trim();
        String password = txtLoginPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Fields cannot be empty");
            return;
        }

        String response = clientService.sendRequest("LOGIN:" + username + ":" + password);
        if (response != null && response.startsWith("SUCCESS")) {
            try {
                navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("System Error", "Cannot load Dashboard");
            }
        } else {
            showAlert("Login Failed", "Invalid username or password");
        }
    }

    // Xử lý Sign Up
    @FXML
    private void handleSignUp(ActionEvent event) {
        String email = txtEmail.getText().trim();
        String password = txtPassword.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Fields cannot be empty");
            return;
        }

        String response = clientService.sendRequest("REGISTER:" + email + ":" + password);
        if ("SUCCESS".equals(response)) {
            showAlert("Success", "Account created! Please login.");
            handleSwitchMode(event);
        } else {
            showAlert("Registration Failed", "Username already exists or Server error");
        }
    }

    // Chuyển đổi giữa Login và Register
    @FXML
    private void handleSwitchMode(ActionEvent event) {
        isLoginMode = !isLoginMode;
        if (isLoginMode) {
            lblTitle.setText("Login");
            loginForm.setVisible(true);
            loginForm.setManaged(true);
            registerForm.setVisible(false);
            registerForm.setManaged(false);
        } else {
            lblTitle.setText("Create Account");
            loginForm.setVisible(false);
            loginForm.setManaged(false);
            registerForm.setVisible(true);
            registerForm.setManaged(true);
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}