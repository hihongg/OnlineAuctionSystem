//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.auction.client.controllers;

import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert.AlertType;

public class LoginController {
    @FXML
    private TextField txtEmail;
    @FXML
    private PasswordField txtPassword;
    private ClientService clientService = new ClientService();
    private NavigationUtils navUtils = new NavigationUtils();

    @FXML
    private void handleSignUp(ActionEvent event) {
        String email = this.txtEmail.getText().trim();
        String password = this.txtPassword.getText();
        if (this.validateInput(email, password)) {
            String response = this.clientService.sendRequest("REGISTER:" + email + ":" + password);
            if ("SUCCESS".equals(response)) {
                try {
                    this.navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
                } catch (Exception e) {
                    e.printStackTrace();
                    this.showAlert("System Error", "Cannot load Dashboard");
                }
            } else {
                this.showAlert("Registration Failed", "Email already exists or Server error");
            }
        }

    }

    private boolean validateInput(String email, String password) {
        if (!email.isEmpty() && !password.isEmpty()) {
            return true;
        } else {
            this.showAlert("Error", "Fields cannot be empty");
            return false;
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText((String)null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
