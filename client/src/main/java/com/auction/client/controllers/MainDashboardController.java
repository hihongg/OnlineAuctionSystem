package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.NavigationUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class MainDashboardController implements Initializable {

    @FXML private FlowPane itemGrid;
    @FXML private TextField txtSearch;

    private NavigationUtils navUtils = new NavigationUtils();
    private ObservableList<AuctionItem> masterData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. Phải gọi 2 hàm này để nạp dữ liệu và vẽ Card ra màn hình
        loadMockData();
        renderCards(masterData);

        // 2. Logic thanh tìm kiếm (giữ nguyên)
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            String filter = newValue.toLowerCase().trim();
            if (filter.isEmpty()) {
                renderCards(masterData);
            } else {
                var filtered = masterData.stream()
                        .filter(item -> item.getName().toLowerCase().contains(filter))
                        .collect(java.util.stream.Collectors.toList());
                renderCards(filtered);
            }
        });
    }

    private void loadMockData() {
        masterData.addAll(
                new AuctionItem("Laptop Gaming Gigabyte A16", 1200.0, java.time.LocalDateTime.now().plusHours(2),
                        "/images/lap.jpg", // Đã sửa lại đường dẫn chuẩn
                        "Laptop cấu hình khủng màn hình 16 inch chuyên đồ họa và gaming nặng."),

                new AuctionItem("Bàn phím cơ Keychron Q1", 80.0, java.time.LocalDateTime.now().plusMinutes(45),
                        "/images/wooting.jpg", // Đã sửa lại đường dẫn chuẩn
                        "Vỏ nhôm full aluminum, kết nối mượt mà, switch gõ siêu êm ái."),

                new AuctionItem("Chuột Logitech G Pro Superlight", 130.0, java.time.LocalDateTime.now().plusDays(1),
                        "/images/chuot.jpg", // Đã sửa lại đường dẫn chuẩn
                        "Chuột không dây siêu nhẹ dành cho game thủ eSports chuyên nghiệp.")
        );
    }

    private void renderCards(java.util.List<AuctionItem> items) {
        itemGrid.getChildren().clear();
        for (AuctionItem item : items) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/ItemCard.fxml"));
                Parent card = loader.load();

                ItemCardController controller = loader.getController();
                controller.setData(item, () -> openItemDetails(item));

                itemGrid.getChildren().add(card);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void openItemDetails(AuctionItem item) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ItemDetail.fxml"));
            Parent root = loader.load();

            ItemDetailController controller = loader.getController();
            controller.setAuctionItem(item);

            Stage stage = (Stage) itemGrid.getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML public void handleCreateItem(ActionEvent event) {
        try { navUtils.switchScene(event, "/CreateItem.fxml", "Đăng bán"); } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML public void handleLogout(ActionEvent event) {
        try { navUtils.switchScene(event, "/LoginView.fxml", "Đăng nhập"); } catch (Exception e) { e.printStackTrace(); }
    }

}