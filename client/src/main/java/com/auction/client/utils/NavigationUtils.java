//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.auction.client.utils;

import java.net.URL;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class NavigationUtils {
    public void switchScene(ActionEvent event, String fxmlPath, String title) throws Exception {
        URL url = this.getClass().getResource(fxmlPath);
        if (url == null) {
            throw new RuntimeException("Không tìm thấy file FXML: " + fxmlPath);
        } else {
            Parent root = (Parent)FXMLLoader.load(url);
            Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.centerOnScreen();
            stage.show();
        }
    }
}
