package com.auction.client.controllers;

import com.auction.client.models.AuctionItem;
import com.auction.client.utils.NavigationUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Callback;

public class MainDashboardController {

    @FXML
    private TableView<AuctionItem> auctionTable;
    @FXML
    private TableColumn<AuctionItem, String> colItemName;
    @FXML
    private TableColumn<AuctionItem, Double> colCurrentBid;
    @FXML
    private TableColumn<AuctionItem, String> colTimeLeft;
    @FXML
    private TableColumn<AuctionItem, Void> colAction;

    private NavigationUtils navUtils = new NavigationUtils();

    @FXML
    public void initialize() {
        colItemName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCurrentBid.setCellValueFactory(new PropertyValueFactory<>("currentBid"));
        colTimeLeft.setCellValueFactory(new PropertyValueFactory<>("timeLeft"));

        addButtonToTable();

        ObservableList<AuctionItem> items = FXCollections.observableArrayList(
                new AuctionItem("Vintage Camera 1950", 150.0, "02:45:10"),
                new AuctionItem("Gaming Laptop i9 RTX 4090", 2500.0, "12:10:05"),
                new AuctionItem("Mechanical Keyboard Custom", 120.5, "00:15:30")
        );

        auctionTable.setItems(items);
    }

    private void addButtonToTable() {
        Callback<TableColumn<AuctionItem, Void>, TableCell<AuctionItem, Void>> cellFactory = new Callback<>() {
            @Override
            public TableCell<AuctionItem, Void> call(final TableColumn<AuctionItem, Void> param) {
                return new TableCell<>() {
                    private final Button btn = new Button("Bid Now");

                    {
                        btn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-cursor: hand;");
                        btn.setOnAction((ActionEvent event) -> {
                            AuctionItem data = getTableView().getItems().get(getIndex());
                            System.out.println("Bid target: " + data.getName());
                        });
                    }

                    @Override
                    public void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            setGraphic(btn);
                        }
                    }
                };
            }
        };
        colAction.setCellFactory(cellFactory);
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            navUtils.switchScene(event, "client/src/main/resources/LoginView.fxml", "Online Auction System - Login");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}