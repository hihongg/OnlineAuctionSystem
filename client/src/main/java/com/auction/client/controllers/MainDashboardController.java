package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MainDashboardController implements Initializable {

    @FXML public FlowPane  itemGrid;
    @FXML public TextField txtSearch;
    @FXML public Label     lblWelcome;
    @FXML public Button    btnAdminPanel;
    @FXML public Button    btnWallet;

    private final NavigationUtils navUtils = new NavigationUtils();
    private final Gson gson = new Gson();
    private final ObservableList<AuctionItem> masterData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            if (lblWelcome != null && ClientService.currentUsername != null) {
                lblWelcome.setText("Xin chào, " + ClientService.currentUsername
                        + " (" + ClientService.currentRole + ")");
            }

            if (btnAdminPanel != null && "ADMIN".equals(ClientService.currentRole)) {
                btnAdminPanel.setVisible(true);
                btnAdminPanel.setManaged(true);
            }

            if (btnWallet != null && ("BIDDER".equals(ClientService.currentRole) || "ADMIN".equals(ClientService.currentRole))) {
                btnWallet.setVisible(true);
                btnWallet.setManaged(true);
            }

            loadDataFromServer();

            if (txtSearch != null) {
                txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
                    String filter = newVal.toLowerCase().trim();
                    if (filter.isEmpty()) {
                        renderCards(masterData);
                    } else {
                        List<AuctionItem> filtered = masterData.stream()
                                .filter(item -> item.getName().toLowerCase().contains(filter))
                                .collect(java.util.stream.Collectors.toList());
                        renderCards(filtered);
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("[Dashboard] Lỗi trong initialize(): " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadDataFromServer() {
        Task<List<AuctionItem>> task = new Task<>() {
            @Override
            protected List<AuctionItem> call() {
                String response = ClientService.sendRequest("GET_ITEMS");
                if (response == null || !response.startsWith("SUCCESS:")) return null;
                String json = response.substring("SUCCESS:".length());
                try {
                    Type listType = new TypeToken<List<AuctionItem>>(){}.getType();
                    return gson.fromJson(json, listType);
                } catch (Exception e) {
                    System.err.println("[Dashboard] Lỗi parse JSON: " + e.getMessage());
                    return null;
                }
            }
        };

        task.setOnSucceeded(e -> {
            List<AuctionItem> items = task.getValue();
            if (items != null && !items.isEmpty()) {
                masterData.setAll(items);
                renderCards(masterData);
            }
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            System.err.println("[Dashboard] Lỗi tải dữ liệu: " + (ex != null ? ex.getMessage() : "unknown"));
        });

        Thread t = new Thread(task, "LoadItemsThread");
        t.setDaemon(true);
        t.start();
    }

    private void renderCards(List<AuctionItem> items) {
        if (itemGrid == null) return;
        itemGrid.getChildren().clear();
        for (AuctionItem item : items) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/ItemCard.fxml"));
                Parent card = loader.load();
                ItemCardController controller = loader.getController();
                controller.setData(item, () -> openItemDetails(item));
                itemGrid.getChildren().add(card);
            } catch (IOException ex) {
                ex.printStackTrace();
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
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        masterData.clear();
        if (itemGrid != null) itemGrid.getChildren().clear();
        loadDataFromServer();
    }

    @FXML
    public void handleCreateItem(ActionEvent event) {
        if (!"SELLER".equals(ClientService.currentRole) && !"ADMIN".equals(ClientService.currentRole)) {
            showAlert("Không có quyền", "Chỉ Seller hoặc Admin mới có thể đăng sản phẩm.");
            return;
        }
        try { navUtils.switchScene(event, "/CreateItem.fxml", "Đăng bán"); }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    @FXML
    public void handleAdminPanel(ActionEvent event) {
        try { navUtils.switchScene(event, "/AdminDashboard.fxml", "Admin Panel"); }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    /**
     * Mở màn hình Ví tiền.
     *
     * FIX CHÍNH XÁC: Tạo WalletController trực tiếp bằng new, sau đó
     * truyền vào FXMLLoader.setController() TRƯỚC KHI gọi load().
     * Cách này hoàn toàn bỏ qua cơ chế reflection/classloader của JavaFX
     * để tìm controller → tránh LoadException.
     * Wallet.fxml KHÔNG được có fx:controller attribute.
     */
    @FXML
    public void handleWallet(ActionEvent event) {
        try {
            URL walletUrl = getClass().getResource("/Wallet.fxml");
            if (walletUrl == null) {
                showAlert("Lỗi", "Không tìm thấy file Wallet.fxml.\nKiểm tra lại resources folder.");
                return;
            }

            WalletController walletController = new WalletController();

            FXMLLoader loader = new FXMLLoader(walletUrl);
            loader.setController(walletController);   // <-- set controller trực tiếp, không qua reflection
            Parent root = loader.load();

            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Ví của tôi");
            Scene currentScene = stage.getScene();
            if (currentScene != null) {
                currentScene.setRoot(root);
            } else {
                stage.setScene(new Scene(root));
            }
            stage.show();

        } catch (Exception ex) {
            ex.printStackTrace();
            StringBuilder sb = new StringBuilder("Không thể mở màn hình Ví:\n");
            Throwable cause = ex;
            while (cause != null) {
                sb.append(cause.getClass().getSimpleName())
                        .append(": ").append(cause.getMessage()).append("\n");
                cause = cause.getCause();
            }
            Platform.runLater(() -> showAlert("Lỗi mở Ví", sb.toString()));
        }
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        ClientService.logout();
        try { navUtils.switchScene(event, "/LoginView.fxml", "Đăng nhập"); }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}