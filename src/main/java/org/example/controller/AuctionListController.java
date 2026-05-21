package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import org.example.service.NetworkService;

import java.io.IOException;

public class AuctionListController {

    // ListView trong auction-list-view.fxml dùng để hiển thị các phiên đấu giá
    @FXML
    private ListView<String> listAuctions;

    private final NetworkService networkService = new NetworkService();

    /**
     * Hàm initialize() tự động chạy sau khi file FXML được load.
     * Lấy danh sách phiên đấu giá từ server thay vì AuctionDataStore local.
     */
    @FXML
    public void initialize() {
        // Xóa danh sách cũ trước khi thêm lại dữ liệu
        listAuctions.getItems().clear();

        loadAuctionsFromServer();

        // Khi chọn một phiên và bấm Enter thì mở màn hình đấu giá
        listAuctions.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleOpenBidding();
                event.consume();
            }
        });

        // Khi double click chuột trái vào một phiên đấu giá thì mở màn hình đấu giá
        listAuctions.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                handleOpenBidding();
                event.consume();
            }
        });
    }

    /**
     * Lấy danh sách phiên đấu giá đang mở từ server.
     *
     * Server hiện trả format:
     * AUCTIONS|auctionId,currentHighestBid,status|auctionId,currentHighestBid,status
     */
    private void loadAuctionsFromServer() {
        String response = networkService.sendMessage("VIEW_ITEMS");

        System.out.println("Server trả về danh sách phiên: " + response);

        if (response == null || response.trim().isEmpty()) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Server không trả về danh sách phiên đấu giá!");
            return;
        }

        if (response.startsWith("ERROR")) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi kết nối",
                    "Không lấy được danh sách phiên đấu giá từ server!");
            return;
        }

        if (response.startsWith("FAIL")) {
            showAlert(Alert.AlertType.WARNING,
                    "Server phản hồi",
                    response);
            return;
        }

        if (!response.startsWith("AUCTIONS")) {
            showAlert(Alert.AlertType.WARNING,
                    "Phản hồi không xác định",
                    response);
            return;
        }

        String[] parts = response.split("\\|");

        if (parts.length == 1) {
            listAuctions.getItems().add("Không có phiên đấu giá nào đang mở");
            return;
        }

        for (int i = 1; i < parts.length; i++) {
            String auctionData = parts[i];

            String[] fields = auctionData.split(",");

            if (fields.length < 4) {
                continue;
            }

            String auctionId = fields[0].trim();
            String title = fields[1].trim();
            String currentPrice = fields[2].trim();
            String status = fields[3].trim();

            String displayText = auctionId
                    + " - " + title
                    + " - Giá hiện tại: " + currentPrice + "$"
                    + " - Trạng thái: " + status;

            listAuctions.getItems().add(displayText);
        }

        if (listAuctions.getItems().isEmpty()) {
            listAuctions.getItems().add("Không có phiên đấu giá nào đang mở");
        }
    }

    /**
     * Hàm này chạy khi người dùng bấm nút "VÀO ĐẤU GIÁ".
     * Nếu chưa chọn phiên nào thì hiện popup cảnh báo.
     * Nếu đã chọn thì chuyển sang màn hình bidding-view.fxml.
     */
    @FXML
    private void handleOpenBidding() {
        // Lấy phiên đấu giá mà người dùng đang chọn trong ListView
        String selectedAuction = listAuctions.getSelectionModel().getSelectedItem();

        // Nếu chưa chọn phiên nào thì báo lỗi
        if (selectedAuction == null) {
            showAlert(Alert.AlertType.WARNING,
                    "Cảnh báo",
                    "Bạn chưa chọn phiên đấu giá!");
            return;
        }

        if (selectedAuction.startsWith("Không có phiên")) {
            showAlert(Alert.AlertType.WARNING,
                    "Cảnh báo",
                    "Hiện chưa có phiên đấu giá nào để tham gia!");
            return;
        }

        try {
            // Load màn hình đấu giá trực tiếp
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/bidding-view.fxml")
            );

            Parent root = loader.load();

            // Truyền phiên đấu giá đang chọn sang BiddingController
            BiddingController biddingController = loader.getController();
            biddingController.setAuctionInfo(selectedAuction);

            // Lấy cửa sổ hiện tại rồi thay scene sang màn hình bidding
            Stage stage = (Stage) listAuctions.getScene().getWindow();
            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Màn hình Đấu giá Trực tiếp");
            stage.show();

        } catch (IOException e) {
            // Nếu không load được FXML thì hiện popup lỗi
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không mở được màn hình đấu giá!");
            e.printStackTrace();
        }
    }

    // Hàm để quay lại màn hình chính
    @FXML
    private void handleBackToHome() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/home-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) listAuctions.getScene().getWindow();

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

    /**
     * Hàm dùng chung để hiện popup thông báo.
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}