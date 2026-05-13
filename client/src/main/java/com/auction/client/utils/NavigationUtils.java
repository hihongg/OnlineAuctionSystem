package com.auction.client.utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.event.ActionEvent;
import java.net.URL;

public class NavigationUtils {
    public void switchScene(ActionEvent event, String fxmlPath, String title) throws Exception {
        // Lấy đường dẫn file giao diện
        URL url = getClass().getResource(fxmlPath);
        if (url == null) {
            throw new RuntimeException("Không tìm thấy file FXML: " + fxmlPath);
        }

        // Tải giao diện mới lên
        Parent root = FXMLLoader.load(url);
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setTitle(title);

        // KHẮC PHỤC LỖI: Thay "ruột" của Scene hiện tại thay vì tạo Scene mới liên tục
        Scene currentScene = stage.getScene();
        if (currentScene != null) {
            currentScene.setRoot(root);
        } else {
            stage.setScene(new Scene(root));
        }

        // Đảm bảo luôn giữ trạng thái phóng to cửa sổ

        stage.show();
    }
}