//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.NavigationUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class ItemDetailController {
    @FXML
    private Label lblName;
    @FXML
    private Label lblPrice;
    @FXML
    private Label lblTime;
    @FXML
    private TextField txtBidAmount;
    @FXML
    private Label lblMessage;
    private AuctionItem currentItem;

    public void setAuctionItem(AuctionItem item) {
        this.currentItem = item;
        this.lblName.setText(item.getName());
        this.lblPrice.setText("Giá hiện tại: $" + item.getCurrentBid());
        this.lblTime.setText("Chi tiết thời gian sẽ đồng bộ sau");
    }

    @FXML
    public void handlePlaceBid(ActionEvent event) {
        try {
            double bidAmount = Double.parseDouble(this.txtBidAmount.getText());
            if (bidAmount <= this.currentItem.getCurrentBid()) {
                this.lblMessage.setStyle("-fx-text-fill: red;");
                this.lblMessage.setText("Lỗi: Giá đặt phải cao hơn giá hiện tại!");
            } else {
                this.lblMessage.setStyle("-fx-text-fill: green;");
                this.lblMessage.setText("Thành công: Bạn đã đặt giá $" + bidAmount);
                this.lblPrice.setText("Giá hiện tại: $" + bidAmount);
                this.txtBidAmount.clear();
            }
        } catch (NumberFormatException var4) {
            this.lblMessage.setStyle("-fx-text-fill: red;");
            this.lblMessage.setText("Lỗi: Vui lòng nhập số hợp lệ.");
        }

    }

    @FXML
    public void handleBackToDashboard(ActionEvent event) {
        try {
            NavigationUtils navUtils = new NavigationUtils();
            navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Lỗi khi quay lại Dashboard");
        }

    }
}
