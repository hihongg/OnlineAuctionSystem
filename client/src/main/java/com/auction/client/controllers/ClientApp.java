package com.auction.client.controllers;

import com.auction.client.utils.ClientSocketManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Entry point của ứng dụng Client.
 *
 * Thay đổi so với bản cũ:
 *   - Kết nối Socket MỘT LẦN khi start() (thay vì mở/đóng mỗi request)
 *   - Override stop() để đóng kết nối khi tắt app
 */
public class ClientApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // --- Bước 1: Kết nối persistent socket tới server ---
        try {
            ClientSocketManager.getInstance().connect(); // localhost:12345
        } catch (Exception e) {
            // Server chưa bật → cảnh báo người dùng, vẫn mở app bình thường
            System.err.println("[APP] Không kết nối được server: " + e.getMessage());
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Cảnh báo");
            alert.setHeaderText("Không thể kết nối Server");
            alert.setContentText("Server chưa chạy. Vui lòng khởi động Server trước.\n"
                    + "Địa chỉ: localhost:12345");
            alert.showAndWait();
        }

        // --- Bước 2: Load màn hình Login ---
        Parent root = FXMLLoader.load(getClass().getResource("/LoginView.fxml"));
        primaryStage.setTitle("Online Auction System - Login");
        primaryStage.setScene(new Scene(root, 800, 500));
        primaryStage.centerOnScreen();

        // --- Bước 3: Đóng socket khi đóng cửa sổ chính ---
        primaryStage.setOnCloseRequest(event -> {
            ClientSocketManager.getInstance().disconnect();
        });

        primaryStage.show();
    }

    /**
     * JavaFX gọi stop() khi Application tắt (dù đóng cửa sổ hay Platform.exit()).
     * Đây là nơi dọn dẹp tài nguyên an toàn nhất.
     */
    @Override
    public void stop() {
        ClientSocketManager.getInstance().disconnect();
        System.out.println("[APP] Ứng dụng đã tắt.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}