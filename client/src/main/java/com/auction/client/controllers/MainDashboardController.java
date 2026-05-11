//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.NavigationUtils;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MainDashboardController implements Initializable {
    @FXML
    private FlowPane itemGrid;

    public void initialize(URL location, ResourceBundle resources) {
        this.loadAuctionItems();
    }

    private void loadAuctionItems() {
        LocalDateTime now = LocalDateTime.now();
        List<AuctionItem> mockItems = Arrays.asList(new AuctionItem("Bàn phím cơ Wooting 60HE", (double)175.5F, now.plusHours(2L).plusMinutes(15L)), new AuctionItem("Laptop Alienware x17 R2", (double)1200.0F, now.plusHours(12L)), new AuctionItem("Tài khoản Minecraft Premium", (double)25.0F, now.plusMinutes(45L)), new AuctionItem("Card đồ họa Intel Arc Graphics", (double)250.0F, now.plusSeconds(10L)));
        if (this.itemGrid != null) {
            this.itemGrid.getChildren().clear();

            for(AuctionItem item : mockItems) {
                VBox card = this.createItemCard(item);
                this.itemGrid.getChildren().add(card);
            }
        }

    }

    private VBox createItemCard(AuctionItem item) {
        VBox card = new VBox((double)10.0F);
        card.setPadding(new Insets((double)15.0F));
        card.setStyle("-fx-border-color: #dcdde1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: white;");
        card.setPrefWidth((double)220.0F);
        card.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(item.getName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #2f3640;");
        nameLabel.setWrapText(true);
        Label priceLabel = new Label("Giá hiện tại: $" + item.getCurrentBid());
        priceLabel.setStyle("-fx-text-fill: #e1b12c; -fx-font-weight: bold; -fx-font-size: 14px;");
        Label timeLabel = new Label();
        timeLabel.setStyle("-fx-text-fill: #e84118; -fx-font-size: 13px; -fx-font-weight: bold;");
        Button bidButton = new Button("Xem chi tiết");
        bidButton.setMaxWidth(Double.MAX_VALUE);
        bidButton.setStyle("-fx-background-color: #00a8ff; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
        Timeline timeline = new Timeline(new KeyFrame[]{new KeyFrame(Duration.seconds((double)1.0F), (event) -> {
            LocalDateTime currentTime = LocalDateTime.now();
            if (!currentTime.isAfter(item.getEndTime()) && !currentTime.isEqual(item.getEndTime())) {
                long hours = ChronoUnit.HOURS.between(currentTime, item.getEndTime());
                long minutes = ChronoUnit.MINUTES.between(currentTime, item.getEndTime()) % 60L;
                long seconds = ChronoUnit.SECONDS.between(currentTime, item.getEndTime()) % 60L;
                timeLabel.setText(String.format("⏱ Còn lại: %02d:%02d:%02d", hours, minutes, seconds));
            } else {
                timeLabel.setText("⏱ Đã kết thúc");
                timeLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px; -fx-font-style: italic;");
                bidButton.setDisable(true);
            }

        }, new KeyValue[0])});
        timeline.setCycleCount(-1);
        timeline.play();
        timeline.play();
        bidButton.setOnAction((event) -> {
            try {
                URL url = this.getClass().getResource("/ItemDetail.fxml");
                System.out.println("Đường dẫn file FXML: " + url);
                if (url == null) {
                    System.out.println("❌ LỖI: Không tìm thấy file FXML!");
                    return;
                }

                FXMLLoader loader = new FXMLLoader(url);
                Parent root = (Parent)loader.load();
                ItemDetailController detailController = (ItemDetailController)loader.getController();
                detailController.setAuctionItem(item);
                Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Lỗi chuyển cảnh sang ItemDetail");
            }

        });
        card.getChildren().addAll(new Node[]{nameLabel, priceLabel, timeLabel, bidButton});
        return card;
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        try {
            NavigationUtils navUtils = new NavigationUtils();
            navUtils.switchScene(event, "/LoginView.fxml", "Online Auction System - Login");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Lỗi khi quay lại màn hình Login");
        }

    }
}
