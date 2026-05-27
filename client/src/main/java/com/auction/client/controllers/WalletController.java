package com.auction.client.controllers;

import com.auction.client.utils.ClientService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller màn hình Ví tiền (Wallet) – dành cho Bidder.
 *
 * Chức năng:
 *   - Hiển thị số dư hiện tại (GET_BALANCE).
 *   - Cho phép nạp tiền (DEPOSIT:<amount>).
 *   - Nút nạp nhanh $100 / $500 / $1,000 / $5,000.
 *   - Quay về Dashboard.
 *
 * QUAN TRỌNG: Controller này được khởi tạo bằng "new WalletController()"
 * trong MainDashboardController.handleWallet() rồi dùng FXMLLoader.setController().
 * File Wallet.fxml KHÔNG có thuộc tính fx:controller.
 */
public class WalletController implements Initializable {

    @FXML public Label     lblBalance;
    @FXML public Label     lblUsername;
    @FXML public Label     lblMessage;
    @FXML public Label     lblDepositTitle;
    @FXML public Label     lblDepositNote;
    @FXML public Button    btnDeposit;
    @FXML public TextField txtAmount;

    // =========================================================================
    // KHỞI TẠO
    // =========================================================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            // ADMIN không có ví – chặn truy cập ngay từ đầu
            if ("ADMIN".equals(ClientService.currentRole)) {
                System.err.println("[WalletController] ADMIN không được phép truy cập Ví.");
                return;
            }

            if (lblUsername != null && ClientService.currentUsername != null) {
                lblUsername.setText(ClientService.currentUsername
                        + " (" + ClientService.currentRole + ")");
            }
            if (lblBalance != null) lblBalance.setText("$0.00");
            if (lblMessage != null) lblMessage.setText("");
            loadBalance();

            // Wallet chỉ dành cho BIDDER và SELLER
            if (lblDepositTitle != null)
                lblDepositTitle.setText("Nạp tiền vào ví");
            if (lblDepositNote != null)
                lblDepositNote.setText("Yêu cầu sẽ được gửi đến Admin để xem xét và phê duyệt.");
            if (btnDeposit != null)
                btnDeposit.setText("Gửi yêu cầu");
        } catch (Exception e) {
            System.err.println("[WalletController] Lỗi khởi tạo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // LẤY SỐ DƯ TỪ SERVER
    // =========================================================================
    private void loadBalance() {
        Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                return ClientService.sendRequest("GET_BALANCE");
            }
        };
        task.setOnSucceeded(e -> {
            String res = task.getValue();
            Platform.runLater(() -> {
                if (lblBalance == null) return;
                if (res != null && res.startsWith("SUCCESS:")) {
                    lblBalance.setText("$" + formatAmount(res.substring("SUCCESS:".length())));
                } else {
                    lblBalance.setText("$0.00");
                    setMessage("❌ Không thể tải số dư: " + res, false);
                }
            });
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            Platform.runLater(() -> {
                if (lblBalance != null) lblBalance.setText("$0.00");
                setMessage("❌ Lỗi kết nối: " + (ex != null ? ex.getMessage() : "unknown"), false);
            });
        });
        new Thread(task, "WalletLoadBalanceThread") {{ setDaemon(true); }}.start();
    }

    // =========================================================================
    // REFRESH
    // =========================================================================
    @FXML
    public void handleRefreshBalance(ActionEvent event) {
        setMessage("⏳ Đang cập nhật...", true);
        loadBalance();
    }

    // =========================================================================
    // NẠP TIỀN (nhập tay)
    // =========================================================================
    @FXML
    public void handleDeposit(ActionEvent event) {
        if (txtAmount == null) return;
        String raw = txtAmount.getText().trim();
        if (raw.isEmpty()) { setMessage("⚠ Vui lòng nhập số tiền muốn nạp.", false); return; }
        try {
            doDeposit(Double.parseDouble(raw));
        } catch (NumberFormatException e) {
            setMessage("⚠ Số tiền không hợp lệ. Hãy nhập số (VD: 500)", false);
        }
    }

    // =========================================================================
    // NẠP NHANH
    // =========================================================================
    @FXML public void handleQuickDeposit100(ActionEvent event)  { quickDeposit(100); }
    @FXML public void handleQuickDeposit500(ActionEvent event)  { quickDeposit(500); }
    @FXML public void handleQuickDeposit1000(ActionEvent event) { quickDeposit(1000); }
    @FXML public void handleQuickDeposit5000(ActionEvent event) { quickDeposit(5000); }

    private void quickDeposit(double amount) {
        if (txtAmount != null) txtAmount.setText(String.valueOf((int) amount));
        doDeposit(amount);
    }

    private void doDeposit(double amount) {
        if (amount <= 0)       { setMessage("⚠ Số tiền phải lớn hơn 0.", false); return; }
        if (amount > 100_000)  { setMessage("⚠ Mỗi lần tối đa $100,000.", false); return; }

        String command = "DEPOSIT_REQUEST:" + amount;
        setMessage("⏳ Đang xử lý...", true);

        Task<String> task = new Task<String>() {
            @Override protected String call() throws Exception {
                return ClientService.sendRequest(command);
            }
        };
        task.setOnSucceeded(e -> {
            String res = task.getValue();
            Platform.runLater(() -> {
                if (res != null && res.startsWith("SUCCESS:")) {
                    String payload = res.substring("SUCCESS:".length());
                    if (txtAmount != null) txtAmount.clear();
                    setMessage("✅ " + payload, true);
                } else {
                    String reason = (res != null && res.startsWith("FAIL:"))
                            ? res.substring(5) : "Lỗi không xác định";
                    setMessage("❌ " + reason, false);
                }
            });
        });
        task.setOnFailed(e -> Platform.runLater(() ->
                setMessage("❌ Lỗi kết nối server. Thử lại sau.", false)));
        new Thread(task, "WalletDepositThread") {{ setDaemon(true); }}.start();
    }

    // =========================================================================
    // QUAY LẠI DASHBOARD
    // Dùng load() thông thường - MainDashboard.fxml load bình thường không bị lỗi.
    // =========================================================================
    @FXML
    public void handleBack(ActionEvent event) {
        try {
            URL dashUrl = getClass().getResource("/MainDashboard.fxml");
            if (dashUrl == null) {
                System.err.println("[WalletController] Không tìm thấy MainDashboard.fxml");
                return;
            }
            FXMLLoader loader = new FXMLLoader(dashUrl);
            Parent root = loader.load();
            Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            stage.setTitle("Auction Dashboard");
            Scene scene = stage.getScene();
            if (scene != null) scene.setRoot(root);
            else stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            System.err.println("[WalletController] Lỗi quay lại: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================
    private void setMessage(String msg, boolean success) {
        if (lblMessage == null) return;
        lblMessage.setText(msg);
        lblMessage.setStyle("-fx-font-size: 14px; -fx-font-style: italic; -fx-text-fill: "
                + (success ? "#27ae60;" : "#e74c3c;"));
    }

    private String formatAmount(String raw) {
        try { return String.format("%,.2f", Double.parseDouble(raw.trim())); }
        catch (NumberFormatException e) { return raw; }
    }
}