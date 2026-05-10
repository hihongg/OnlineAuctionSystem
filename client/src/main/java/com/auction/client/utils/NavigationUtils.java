package com.auction.client.utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.event.ActionEvent;
import java.io.File;
import java.net.URL;

public class NavigationUtils {
    public void switchScene(ActionEvent event, String fxmlPath, String title) throws Exception {
        // Dùng getResource chuẩn xác hơn dùng File rất nhiều
        URL url = getClass().getResource(fxmlPath);
        if (url == null) {
            throw new RuntimeException("Không tìm thấy file FXML: " + fxmlPath);
        }

        Parent root = FXMLLoader.load(url);
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        stage.centerOnScreen();
        stage.show();
    }
}
