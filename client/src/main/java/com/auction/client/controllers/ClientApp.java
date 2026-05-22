package com.auction.client.controllers;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.File;

public class ClientApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // TẠM THỜI: Đổi đường dẫn từ LoginView.fxml sang MainDashboard.fxml để mở thẳng trang Dashboard
        Parent root = FXMLLoader.load(new File("client/src/main/resources/RegisterView.fxml").toURI().toURL());

        primaryStage.setTitle("Online Auction System - Dashboard (Giai đoạn thiết kế UI)");
        primaryStage.setScene(new Scene(root, 800, 500));
        primaryStage.centerOnScreen();
        primaryStage.setMaximized(true); // Tự động phóng to toàn màn hình
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}