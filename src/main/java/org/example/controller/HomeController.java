package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.example.model.User;

public class HomeController {

    @FXML
    private Label welcomeLabel;

    private User currentUser;

    public void setUser(User user){
        this.currentUser = user;
        welcomeLabel.setText("Xin chào, " + user.getUsername() + " (" + user.getRole() + ")");
    }

    // ĐÃ SỬA: Hàm này giờ sẽ mở màn hình Bidding thay vì hiện Popup
    @FXML
    private void handleViewAuctions() {
        try {
            // 1. Tìm và tải file giao diện đấu giá
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/views/bidding-view.fxml"));
            Parent root = fxmlLoader.load();

            // 2. Tạo một cửa sổ mới
            Stage stage = new Stage();
            stage.setTitle("Màn hình Đấu giá Trực tiếp");
            stage.setScene(new Scene(root));

            // 3. Hiển thị cửa sổ đấu giá lên
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Lỗi: Không thể tải file bidding-view.fxml");
        }
    }
}