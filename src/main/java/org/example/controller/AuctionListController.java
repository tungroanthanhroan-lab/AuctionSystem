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
public class AuctionListController {

    @FXML
    private ListView<String> listAuctions;

    private final NetworkService networkService = new NetworkService();
    private final Map<String, String> auctionInfoMap = new HashMap<>();
    @FXML
    public void initialize() {
        listAuctions.getItems().clear();

        loadAuctionsFromServer();

        listAuctions.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleOpenBidding();
                event.consume();
            }
        });

        listAuctions.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                handleOpenBidding();
                event.consume();
            }
        });
    }

    private void loadAuctionsFromServer() {
        String response = networkService.sendMessage("VIEW_ITEMS");
        auctionInfoMap.clear();
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
                    response.replace("ERROR|", ""));
            return;
        }

        if (response.startsWith("FAIL")) {
            showAlert(Alert.AlertType.WARNING,
                    "Server phản hồi",
                    response.replace("FAIL|", ""));
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
            String[] fields = auctionData.split(",", -1);

            /*
             * Backend có thể trả:
             * auctionId,title,currentPrice,status,endTime
             *
             * hoặc:
             * auctionId,title,currentPrice,status,startTime,endTime
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
            /*
             * Backend hiện tại:
             * auctionId,title,currentPrice,status,currentLeader,endTime
             */
            if (fields.length >= 6) {
                currentLeader = fields[4].trim();
                endTime = fields[5].trim();
            } else if (fields.length == 5) {
                endTime = fields[4].trim();
            }

            /*
             * rawInfo là chuỗi kỹ thuật dùng để truyền sang BiddingController.
             * Giữ format cũ để BiddingController parse được giá, trạng thái, endTime.
             */
            String rawInfo = auctionId
                    + " - " + title
                    + " - Giá hiện tại: " + currentPrice + "$"
                    + " - Trạng thái: " + status;

            if (!currentLeader.isEmpty() && !currentLeader.equals("-")) {
                rawInfo += " - Người dẫn đầu: " + currentLeader;
            }

            if (!endTime.isEmpty()) {
                rawInfo += " - Kết thúc: " + endTime;
            }

            /*
             * displayText là chuỗi thân thiện cho người dùng nhìn trong danh sách.
             */
            String displayText = title
                    + "  •  Giá: " + currentPrice + "$"
                    + "  •  " + formatStatus(status);

            if (!currentLeader.isEmpty() && !currentLeader.equals("-")) {
                displayText += "  •  Dẫn đầu: " + currentLeader;
            }

            if (!endTime.isEmpty()) {
                displayText += "  •  Kết thúc: " + formatDateTime(endTime);
            }

            /*
             * Lưu mapping:
             * người dùng chọn displayText đẹp,
             * nhưng khi mở Bidding thì truyền rawInfo.
             */
            auctionInfoMap.put(displayText, rawInfo);

            listAuctions.getItems().add(displayText);
        }

        if (listAuctions.getItems().isEmpty()) {
            listAuctions.getItems().add("Không có phiên đấu giá nào đang mở");
        }
    }

    @FXML
    private void handleOpenBidding() {
        String selectedAuction = listAuctions.getSelectionModel().getSelectedItem();

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
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/bidding-view.fxml")
            );

            Parent root = loader.load();

            BiddingController biddingController = loader.getController();

            String rawAuctionInfo = auctionInfoMap.getOrDefault(selectedAuction, selectedAuction);
            biddingController.setAuctionInfo(rawAuctionInfo);
            Stage stage = (Stage) listAuctions.getScene().getWindow();

            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Màn hình Đấu giá Trực tiếp");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không mở được màn hình đấu giá!");
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

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}