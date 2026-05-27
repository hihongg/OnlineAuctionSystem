package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.ClientService;
import com.auction.client.utils.NavigationUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.lang.reflect.Type;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class SellerItemsController implements Initializable {

    @FXML public TableView<AuctionItem>       tableItems;
    @FXML public TableColumn<AuctionItem, Integer> colId;
    @FXML public TableColumn<AuctionItem, String>  colName;
    @FXML public TableColumn<AuctionItem, String>  colCategory;
    @FXML public TableColumn<AuctionItem, Double>  colPrice;
    @FXML public TableColumn<AuctionItem, String>  colStatus;
    @FXML public TableColumn<AuctionItem, String>  colEndTime;
    @FXML public Label lblTitle;
    @FXML public Button btnRefresh;

    private final NavigationUtils navUtils = new NavigationUtils();
    private final Gson gson = new Gson();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupColumns();
        loadMyItems();
    }

    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("currentHighestBid"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colEndTime.setCellValueFactory(new PropertyValueFactory<>("endTimeFormatted"));

        // THÊM LỆNH NÀY VÀO ĐÂY ĐỂ BẢNG TỰ ĐỘNG GIÃN CỘT
        tableItems.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    switch (status) {
                        case "RUNNING"  -> setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                        case "OPEN"     -> setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                        case "FINISHED" -> setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                        case "PAID"     -> setStyle("-fx-text-fill: #2980b9; -fx-font-weight: bold;");
                        case "CANCELED" -> setStyle("-fx-text-fill: #95a5a6; -fx-font-weight: bold;");
                        default         -> setStyle("");
                    }
                }
            }
        });
    }

    private void loadMyItems() {
        Task<List<AuctionItem>> task = new Task<>() {
            @Override
            protected List<AuctionItem> call() {
                String response = ClientService.sendRequest("GET_MY_ITEMS");
                if (response == null || !response.startsWith("SUCCESS:")) return null;
                String json = response.substring("SUCCESS:".length());
                Type listType = new TypeToken<List<AuctionItem>>() {}.getType();
                return gson.fromJson(json, listType);
            }
        };

        task.setOnSucceeded(e -> {
            List<AuctionItem> items = task.getValue();
            if (items != null) {
                tableItems.getItems().setAll(items);
            } else {
                tableItems.getItems().clear();
            }
        });

        task.setOnFailed(e -> showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể tải danh sách sản phẩm."));
        Thread t = new Thread(task, "LoadMyItemsThread");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleRefresh(ActionEvent event) { loadMyItems(); }

    @FXML
    public void handleEdit(ActionEvent event) {
        AuctionItem selected = tableItems.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn sản phẩm", "Vui lòng chọn một sản phẩm trong bảng để sửa.");
            return;
        }
        if ("RUNNING".equals(selected.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Không thể sửa", "Không thể sửa sản phẩm đang diễn ra (RUNNING).");
            return;
        }
        if ("FINISHED".equals(selected.getStatus()) || "PAID".equals(selected.getStatus()) || "CANCELED".equals(selected.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Không thể sửa", "Không thể sửa sản phẩm đã kết thúc.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/EditItem.fxml"));
            Parent root = loader.load();
            EditItemController controller = loader.getController();
            controller.setItem(selected);
            Stage stage = (Stage) tableItems.getScene().getWindow();
            stage.setTitle("Chỉnh sửa sản phẩm");
            stage.getScene().setRoot(root);
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể mở màn hình sửa: " + ex.getMessage());
        }
    }

    @FXML
    public void handleDelete(ActionEvent event) {
        AuctionItem selected = tableItems.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn sản phẩm", "Vui lòng chọn một sản phẩm trong bảng để xóa.");
            return;
        }
        if ("RUNNING".equals(selected.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Không thể xóa", "Không thể xóa sản phẩm đang diễn ra (RUNNING).");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận xóa");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn xóa sản phẩm \"" + selected.getName() + "\" không?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        Task<String> task = new Task<>() {
            @Override
            protected String call() { return ClientService.sendRequest("DELETE_ITEM:" + selected.getId()); }
        };

        task.setOnSucceeded(e -> {
            String response = task.getValue();
            if (response != null && response.startsWith("SUCCESS")) {
                showAlert(Alert.AlertType.INFORMATION, "Đã xóa", "Sản phẩm \"" + selected.getName() + "\" đã được xóa.");
                loadMyItems();
            } else {
                String reason = (response != null && response.startsWith("FAIL:")) ? response.substring(5) : response;
                showAlert(Alert.AlertType.ERROR, "Xóa thất bại", reason);
            }
        });
        Thread t = new Thread(task, "DeleteItemThread");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleBack(ActionEvent event) {
        try { navUtils.switchScene(event, "/MainDashboard.fxml", "Auction Dashboard"); }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}