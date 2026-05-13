package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.example.service.AppSession;

import java.io.IOException;

public class SellerHomeController {

    @FXML
    private Label lblWelcome;

    @FXML
    public void initialize() {
        String username = AppSession.getCurrentUsername();

        if (username == null || username.isEmpty()) {
            lblWelcome.setText("Xin chào Seller");
        } else {
            lblWelcome.setText("Xin chào Seller: " + username);
        }
    }

    @FXML
    private void handleOpenCreateAuction() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/create-auction-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) lblWelcome.getScene().getWindow();
            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Tạo phiên đấu giá mới");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không mở được màn hình tạo phiên đấu giá!");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/login-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) lblWelcome.getScene().getWindow();

            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Đăng nhập");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không quay lại được màn hình đăng nhập!");
            e.printStackTrace();
        }
    }
//    Đã gửi
//    Soạn
//    Viết cho


    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}