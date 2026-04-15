package com.auction.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML; // 1. Phải có dòng import này để hết báo đỏ chữ FXML

public class LoginController {
    @FXML
    public void handleSignUp(ActionEvent event) {
        System.out.println("Nút đăng ký đã được bấm thành công!");
    }

}