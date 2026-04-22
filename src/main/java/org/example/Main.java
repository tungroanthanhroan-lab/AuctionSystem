package org.example;

// Import Application để tạo app JavaFX
import javafx.application.Application;

// Import FXMLLoader để load file FXML
import javafx.fxml.FXMLLoader;

// Import Parent làm node gốc của giao diện
import javafx.scene.Parent;

// Import Scene để chứa giao diện
import javafx.scene.Scene;

// Import Stage là cửa sổ chính
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Load file giao diện login từ thư mục resources/views
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/login-view.fxml"));
        Parent root = loader.load();

        // Tạo scene với kích thước 400x300
        Scene scene = new Scene(root, 400, 300);

        // Đặt tiêu đề cửa sổ
        stage.setTitle("Auction System - Login");

        // Gắn scene vào stage
        stage.setScene(scene);

        // Hiển thị cửa sổ
        stage.show();
    }

    public static void main(String[] args) {
        // Khởi chạy ứng dụng JavaFX
        launch(args);
    }
}