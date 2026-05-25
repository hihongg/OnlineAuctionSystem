package com.auction.client.utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.event.ActionEvent;

import java.net.URL;

public class NavigationUtils {

    /**
     * Chuyển scene - PHẢI được gọi từ JavaFX Application Thread.
     */
    public void switchScene(ActionEvent event, String fxmlPath, String title) throws Exception {
        URL url = NavigationUtils.class.getResource(fxmlPath);
        if (url == null) {
            throw new RuntimeException("Không tìm thấy file FXML: " + fxmlPath);
        }

        FXMLLoader loader = new FXMLLoader(url);
        loader.setClassLoader(NavigationUtils.class.getClassLoader());
        Parent root = loader.load();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setTitle(title);

        Scene currentScene = stage.getScene();
        if (currentScene != null) {
            currentScene.setRoot(root);
        } else {
            stage.setScene(new Scene(root));
        }
        stage.show();
    }
}