package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.example.model.User;

import java.io.IOException;

public class HomeController {

    @FXML
    private Label welcomeLabel;

    private User currentUser;

    public void setUser(User user) {
        this.currentUser = user;
        welcomeLabel.setText("Xin chào, " + user.getUsername() + " (" + user.getRole() + ")");
    }

    /**
     * Khi bấm nút "Xem danh sách đấu giá",
     * chuyển từ màn hình Home sang màn hình danh sách phiên đấu giá.
     */
    @FXML
    private void handleViewAuctions() {
        try {
            // Load màn hình danh sách phiên đấu giá
            FXMLLoader fxmlLoader = new FXMLLoader(
                    getClass().getResource("/views/auction-list-view.fxml")
            );

            Parent root = fxmlLoader.load();

            // Lấy cửa sổ hiện tại từ welcomeLabel
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();

            // Thay scene hiện tại bằng scene danh sách đấu giá
            stage.setScene(new Scene(root));
            stage.setTitle("Danh sách phiên đấu giá");
            stage.show();

        } catch (IOException e) {
            // Hiện popup nếu không mở được màn hình danh sách
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không thể tải file auction-list-view.fxml");
            e.printStackTrace();
        }
    }
    /**
     * Hàm hiện popup lỗi.
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}