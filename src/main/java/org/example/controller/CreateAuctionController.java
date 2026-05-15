package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.service.AuctionDataStore;

import java.io.IOException;

public class CreateAuctionController {

    @FXML
    private TextField txtAuctionName;

    @FXML
    private TextField txtStartPrice;

    /*
     * Hàm chạy khi người dùng bấm nút TẠO PHIÊN.
     *
     * Luồng xử lý:
     * 1. Lấy tên sản phẩm và giá khởi điểm.
     * 2. Kiểm tra dữ liệu nhập.
     * 3. Thêm phiên mới vào AuctionDataStore.
     * 4. Hiện popup tạo thành công.
     * 5. Quay lại Home.
     */
    @FXML
    private void handleCreateAuction() {
        String auctionName = txtAuctionName.getText();
        String startPriceText = txtStartPrice.getText();

        if (auctionName == null || auctionName.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING,
                    "Cảnh báo",
                    "Bạn chưa nhập tên sản phẩm!");
            return;
        }

        if (startPriceText == null || startPriceText.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING,
                    "Cảnh báo",
                    "Bạn chưa nhập giá khởi điểm!");
            return;
        }

        try {
            double startPrice = Double.parseDouble(startPriceText.trim());

            if (startPrice < 0) {
                showAlert(Alert.AlertType.ERROR,
                        "Lỗi",
                        "Giá khởi điểm không được âm!");
                return;
            }

            /*
             * Tạm thời tạo id bằng số lượng phiên hiện có + 1.
             * Vì hiện tại AuctionDataStore vẫn là dữ liệu demo phía client.
             */
            int newId = AuctionDataStore.getCurrentPrices().size() + 1;
            String fullAuctionName = newId + " - " + auctionName.trim();

            AuctionDataStore.addNewAuction(fullAuctionName, startPrice);

            showAlert(Alert.AlertType.INFORMATION,
                    "Thành công",
                    "Đã tạo phiên đấu giá: " + fullAuctionName);

            handleBackToHome();

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi nhập liệu",
                    "Giá khởi điểm phải là số!");
        }
    }

    /*
     * Quay lại màn Home.
     */
    @FXML
    private void handleBackToHome() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/home-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) txtAuctionName.getScene().getWindow();
            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Auction System - Home");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không quay lại được màn hình chính!");
            e.printStackTrace();
        }
    }

    /*
     * Giữ lại hàm cũ để nếu FXML hoặc code nào vẫn gọi handleBackToSellerHome
     * thì vẫn quay về Home, tránh lỗi.
     */
    @FXML
    private void handleBackToSellerHome() {
        handleBackToHome();
    }

    /*
     * Hàm dùng chung để hiện popup.
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}