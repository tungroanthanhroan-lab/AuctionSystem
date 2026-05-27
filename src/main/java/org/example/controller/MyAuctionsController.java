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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class MyAuctionsController {

    @FXML
    private ListView<String> listMyAuctions;

    private final NetworkService networkService = new NetworkService();

    /*
     * Map displayText đẹp -> rawInfo dùng để truyền sang BiddingController.
     */
    private final Map<String, String> auctionInfoMap = new HashMap<>();

    @FXML
    public void initialize() {
        loadMyAuctionsFromServer();

        listMyAuctions.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleOpenAuction();
                event.consume();
            }
        });

        listMyAuctions.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                handleOpenAuction();
                event.consume();
            }
        });
    }

    private void loadMyAuctionsFromServer() {
        listMyAuctions.getItems().clear();
        auctionInfoMap.clear();

        String response = networkService.sendMessage("MY_AUCTIONS");

        System.out.println("Server trả về Phiên của tôi: " + response);

        if (response == null || response.trim().isEmpty()) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Server không trả về danh sách phiên của tôi!");
            return;
        }

        if (response.startsWith("ERROR")) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi kết nối",
                    response.replace("ERROR|", ""));
            return;
        }

        if (response.startsWith("FAIL")) {
            showAlert(Alert.AlertType.WARNING,
                    "Server phản hồi",
                    response.replace("FAIL|", ""));
            return;
        }

        if (!response.startsWith("MY_AUCTIONS")) {
            showAlert(Alert.AlertType.WARNING,
                    "Phản hồi không xác định",
                    response);
            return;
        }

        String[] parts = response.split("\\|");

        if (parts.length == 1) {
            listMyAuctions.getItems().add("Bạn chưa tạo phiên đấu giá nào.");
            return;
        }

        for (int i = 1; i < parts.length; i++) {
            String auctionData = parts[i];
            String[] fields = auctionData.split(",", -1);

            /*
             * Format:
             * auctionId,title,currentHighestBid,status,endTime
             */
            if (fields.length < 4) {
                continue;
            }

            String auctionId = fields[0].trim();
            String title = fields[1].trim();
            String currentPrice = fields[2].trim();
            String status = fields[3].trim();

            String currentLeader = "";
            String endTime = "";

            if (fields.length >= 6) {
                currentLeader = fields[4].trim();
                endTime = fields[5].trim();
            } else if (fields.length == 5) {
                endTime = fields[4].trim();
            }

            /*
             * rawInfo giữ format để BiddingController parse được.
             */
            String rawInfo = auctionId
                    + " - " + title
                    + " - Giá hiện tại: " + currentPrice + "$"
                    + " - Trạng thái: " + status;

            if (!currentLeader.isEmpty() && !currentLeader.equals("-")) {
                rawInfo += " - Người dẫn đầu: " + currentLeader;
            }

            if (!endTime.isEmpty() && !endTime.equals("-")) {
                rawInfo += " - Kết thúc: " + endTime;
            }

            String displayText = title
                    + "  •  Giá: " + currentPrice + "$"
                    + "  •  " + formatStatus(status);

            if (!currentLeader.isEmpty() && !currentLeader.equals("-")) {
                displayText += "  •  Dẫn đầu: " + currentLeader;
            }

            if (!endTime.isEmpty() && !endTime.equals("-")) {
                displayText += "  •  Kết thúc: " + formatDateTime(endTime);
            }

            auctionInfoMap.put(displayText, rawInfo);
            listMyAuctions.getItems().add(displayText);
        }

        if (listMyAuctions.getItems().isEmpty()) {
            listMyAuctions.getItems().add("Bạn chưa tạo phiên đấu giá nào.");
        }
    }

    @FXML
    private void handleOpenAuction() {
        String selectedAuction = listMyAuctions.getSelectionModel().getSelectedItem();

        if (selectedAuction == null) {
            showAlert(Alert.AlertType.WARNING,
                    "Cảnh báo",
                    "Bạn chưa chọn phiên đấu giá!");
            return;
        }

        if (selectedAuction.startsWith("Bạn chưa tạo")) {
            showAlert(Alert.AlertType.WARNING,
                    "Cảnh báo",
                    "Bạn chưa có phiên đấu giá nào để xem!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/bidding-view.fxml")
            );

            Parent root = loader.load();

            BiddingController biddingController = loader.getController();

            String rawInfo = auctionInfoMap.getOrDefault(selectedAuction, selectedAuction);
            biddingController.setAuctionInfo(rawInfo);

            Stage stage = (Stage) listMyAuctions.getScene().getWindow();

            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Chi tiết phiên đấu giá");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không mở được màn chi tiết phiên!");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBackToHome() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/home-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) listMyAuctions.getScene().getWindow();

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

    private String formatStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "Không xác định";
        }

        if (status.equalsIgnoreCase("OPEN") || status.equalsIgnoreCase("RUNNING")) {
            return "Đang mở";
        }

        if (status.equalsIgnoreCase("FINISHED")) {
            return "Đã kết thúc";
        }

        if (status.equalsIgnoreCase("CANCELED")) {
            return "Đã hủy";
        }

        if (status.equalsIgnoreCase("PAID")) {
            return "Đã thanh toán";
        }

        return status;
    }

    private String formatDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "Không xác định";
        }

        try {
            LocalDateTime dateTime = LocalDateTime.parse(value.trim().replace(" ", "T"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy");
            return dateTime.format(formatter);
        } catch (Exception e) {
            return value;
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}