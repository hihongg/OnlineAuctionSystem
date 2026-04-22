package com.auction.client.controllers;

import com.auction.client.utils.NavigationUtils;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.event.ActionEvent;

public class LoginController {

    @FXML
    private TextField txtEmail;

    @FXML
    private PasswordField txtPassword;

    private ClientService clientService = new ClientService();
    private NavigationUtils navUtils = new NavigationUtils();

    @FXML
    private void handleSignUp(ActionEvent event) {
        String email = txtEmail.getText().trim();
        String password = txtPassword.getText();

        if (validateInput(email, password)) {
            String response = clientService.sendRequest("REGISTER:" + email + ":" + password);

            if ("SUCCESS".equals(response)) {
                try {
                    navUtils.switchScene(event, "client/src/main/resources/MainDashboard.fxml", "Auction Dashboard");
                } catch (Exception e) {
                    showAlert("System Error", "Cannot load Dashboard");
                }
            } else {
                showAlert("Registration Failed", "Email already exists or Server error");
            }
        }
    }

    private boolean validateInput(String email, String password) {
        if (email.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Fields cannot be empty");
            return false;
        }
        return true;
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}