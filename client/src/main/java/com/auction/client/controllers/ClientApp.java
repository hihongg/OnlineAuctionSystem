package com.auction.client.controllers;

import com.auction.client.utils.ClientService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 1. Kết nối tới Server ngay khi khởi động app
        ClientService.connect();

        // 2. Load màn hình đăng nhập
        Parent root = FXMLLoader.load(getClass().getResource("/LoginView.fxml"));

        primaryStage.setTitle("Online Auction System");
        primaryStage.setScene(new Scene(root, 800, 500));

        // ==========================================
        // THIẾT LẬP PHÓNG TO TỐI ĐA (MAXIMIZED)
        // ==========================================
        primaryStage.setMaximized(true);

        // 3. Ngắt kết nối sạch khi người dùng đóng cửa sổ
        primaryStage.setOnCloseRequest(event -> ClientService.disconnect());

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}