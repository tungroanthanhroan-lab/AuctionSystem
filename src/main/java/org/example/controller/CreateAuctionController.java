package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.service.NetworkService;

import java.io.IOException;

public class CreateAuctionController {

    @FXML
    private TextField txtAuctionName;

    @FXML
    private TextField txtStartPrice;

    @FXML
    private TextField txtEndTime;

    private final NetworkService networkService = new NetworkService();

    /*
     * Hàm chạy khi người dùng bấm nút TẠO PHIÊN.
     *
     * Luồng xử lý:
     * 1. Lấy tên sản phẩm, giá khởi điểm và thời gian kết thúc.
     * 2. Kiểm tra dữ liệu nhập.
     * 3. Gửi CREATE_AUCTION lên server.
     * 4. Nếu server phản hồi thành công thì quay lại Home.
     */
    @FXML
    private void handleCreateAuction() {
        String auctionName = txtAuctionName.getText();
        String startPriceText = txtStartPrice.getText();
        String endTime = txtEndTime.getText();

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

        if (endTime == null || endTime.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING,
                    "Cảnh báo",
                    "Bạn chưa nhập thời gian kết thúc!");
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
             * Format gửi lên server:
             * CREATE_AUCTION|title|startingPrice|endTime
             *
             * Ví dụ:
             * CREATE_AUCTION|Laptop Gaming|1000|2026-12-31T23:59
             */
            String message = "CREATE_AUCTION|"
                    + auctionName.trim() + "|"
                    + startPrice + "|"
                    + endTime.trim();

            String response = networkService.sendMessage(message);

            System.out.println("Server trả về khi tạo phiên: " + response);

            if (response.startsWith("SUCCESS")) {
                showAlert(Alert.AlertType.INFORMATION,
                        "Thành công",
                        response.replace("SUCCESS|", ""));

                handleBackToHome();
                return;
            }

            if (response.startsWith("FAIL")) {
                showAlert(Alert.AlertType.ERROR,
                        "Tạo phiên thất bại",
                        response.replace("FAIL|", ""));
                return;
            }

            if (response.startsWith("ERROR")) {
                showAlert(Alert.AlertType.ERROR,
                        "Lỗi kết nối",
                        "Không kết nối được tới server!");
                return;
            }

            showAlert(Alert.AlertType.WARNING,
                    "Phản hồi không xác định",
                    response);

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