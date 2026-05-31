package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.service.NetworkService;

/**
 * Controller cho dialog Đặt giá tự động (Auto-Bid).
 *
 * Giao tiếp với server qua 2 lệnh:
 *   SET_AUTO_BID|auctionId|maxBid|increment  → đăng ký / cập nhật auto-bid
 *   CANCEL_AUTO_BID|auctionId                → hủy auto-bid
 *
 * Cách dùng:
 *   FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/auto-bid-view.fxml"));
 *   Parent root = loader.load();
 *   AutoBidController ctrl = loader.getController();
 *   ctrl.init(auctionId, currentPrice, networkService);
 *   Stage dialog = new Stage();
 *   dialog.initModality(Modality.APPLICATION_MODAL);
 *   dialog.setScene(new Scene(root));
 *   dialog.showAndWait();
 */
public class AutoBidController {

    // ── FXML fields ────────────────────────────────────────────────────────
    @FXML private Label lblAuctionTitle;
    @FXML private Label lblCurrentPrice;
    @FXML private TextField txtMaxBid;
    @FXML private TextField txtIncrement;
    @FXML private Button btnActivate;
    @FXML private Button btnCancel;
    @FXML private Button btnClose;
    @FXML private Label lblStatus;

    // ── State ──────────────────────────────────────────────────────────────
    private String auctionId;
    private double currentPrice;
    private NetworkService networkService;

    // ── Init (được gọi từ BiddingController trước khi show dialog) ─────────
    public void init(String auctionId, double currentPrice, NetworkService networkService) {
        this.auctionId      = auctionId;
        this.currentPrice   = currentPrice;
        this.networkService = networkService;

        lblCurrentPrice.setText("Giá hiện tại: " + currentPrice + " $");
        lblAuctionTitle.setText("Phiên: " + auctionId);
        lblStatus.setText("");
    }

    // ── Handlers ───────────────────────────────────────────────────────────

    @FXML
    private void handleActivate() {
        String maxBidStr   = txtMaxBid.getText().trim();
        String incrementStr = txtIncrement.getText().trim();

        // Validate input
        if (maxBidStr.isEmpty() || incrementStr.isEmpty()) {
            showError("Vui lòng nhập đủ Giá tối đa và Bước giá.");
            return;
        }

        double maxBid, increment;
        try {
            maxBid    = Double.parseDouble(maxBidStr);
            increment = Double.parseDouble(incrementStr);
        } catch (NumberFormatException e) {
            showError("Giá tối đa và Bước giá phải là số hợp lệ.");
            return;
        }

        if (maxBid <= currentPrice) {
            showError("Giá tối đa phải lớn hơn giá hiện tại (" + currentPrice + " $).");
            return;
        }

        if (increment <= 0) {
            showError("Bước giá phải lớn hơn 0.");
            return;
        }

        if (increment >= (maxBid - currentPrice)) {
            showError("Bước giá quá lớn so với khoảng giá còn lại.\n"
                    + "Bước giá nên nhỏ hơn " + (maxBid - currentPrice) + " $.");
            return;
        }

        // Gửi lệnh lên server
        String command  = "SET_AUTO_BID|" + auctionId + "|" + maxBid + "|" + increment;
        String response = networkService.sendMessage(command);

        if (response == null) {
            showError("Không nhận được phản hồi từ server.");
            return;
        }

        if (response.startsWith("SUCCESS")) {
            lblStatus.setText("✅ Auto-bid đã được kích hoạt!\nGiá tối đa: " + maxBid
                    + " $  |  Bước giá: " + increment + " $");
            lblStatus.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
            btnCancel.setDisable(false);
            btnActivate.setText("Cập nhật Auto-Bid");
        } else {
            showError(response.replace("FAIL|", ""));
        }
    }

    @FXML
    private void handleCancel() {
        String command  = "CANCEL_AUTO_BID|" + auctionId;
        String response = networkService.sendMessage(command);

        if (response != null && response.startsWith("SUCCESS")) {
            lblStatus.setText("Auto-bid đã được hủy.");
            lblStatus.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            txtMaxBid.clear();
            txtIncrement.clear();
            btnCancel.setDisable(true);
            btnActivate.setText("Kích hoạt Auto-Bid");
        } else {
            showError(response != null ? response.replace("FAIL|", "") : "Lỗi khi hủy auto-bid.");
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) btnClose.getScene().getWindow();
        stage.close();
    }

    // ── Helper ─────────────────────────────────────────────────────────────
    private void showError(String msg) {
        lblStatus.setText("⚠ " + msg);
        lblStatus.setStyle("-fx-text-fill: #dc2626;");
    }
}