package org.example.controller;

// Import annotation FXML để liên kết với file FXML
import javafx.fxml.FXML;

// Import Alert để hiện popup test
import javafx.scene.control.Alert;

public class HomeController {

    // Hàm này được gọi khi người dùng bấm nút "Xem danh sách đấu giá"
    @FXML
    private void handleViewAuctions() {
        // Tạo popup thông báo đơn giản để test màn Home
        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        // Đặt tiêu đề popup
        alert.setTitle("Thông báo");

        // Không dùng header
        alert.setHeaderText(null);

        // Nội dung thông báo
        alert.setContentText("Đây là màn Home. Bước sau sẽ làm danh sách auction.");

        // Hiển thị popup
        alert.showAndWait();
    }
}