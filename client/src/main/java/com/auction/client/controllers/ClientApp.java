//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.auction.client.controllers;

import java.io.File;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {
    public void start(Stage primaryStage) throws Exception {
        Parent root = (Parent)FXMLLoader.load((new File("client/src/main/resources/LoginView.fxml")).toURI().toURL());
        primaryStage.setTitle("Online Auction System - Login");
        primaryStage.setScene(new Scene(root, (double)800.0F, (double)500.0F));
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
