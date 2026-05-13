package org.example.controller;

import org.example.service.AuctionDataStore;
import javafx.scene.input.KeyCode;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import javafx.scene.input.MouseButton;
import java.io.IOException;

public class AuctionListController {

    // ListView trong auction-list-view.fxml dùng để hiển thị các phiên đấu giá
    @FXML
    private ListView<String> listAuctions;

    /**
     * Hàm initialize() tự động chạy sau khi file FXML được load.
     * Tạm thời hard-code danh sách phiên đấu giá để demo giao diện.
     * Sau này có thể thay bằng dữ liệu lấy từ server/database.
     */
    @FXML
    public void initialize() {
        // Xóa danh sách cũ trước khi thêm lại dữ liệu
        listAuctions.getItems().clear();

        // Lấy danh sách phiên đấu giá từ AuctionDataStore
        // để nếu giá đã được cập nhật thì list cũng hiện giá mới
        AuctionDataStore.getCurrentPrices().forEach((auctionName, price) -> {
            listAuctions.getItems().add(
                    auctionName + " - Giá hiện tại: " + price + "$"
            );
        });

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

        try {
            // Load màn hình đấu giá trực tiếp
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/bidding-view.fxml")
            );

            Parent root = loader.load();
            //// Truyền phiên đấu giá đang chọn sang BiddingController
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
    //hàm để quay lại màn hình chính
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