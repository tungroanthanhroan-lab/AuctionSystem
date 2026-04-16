package org.example.controller;

// Import annotation FXML để liên kết UI với code Java
import javafx.fxml.FXML;

// Import FXMLLoader để load file FXML màn hình khác
import javafx.fxml.FXMLLoader;

// Import Parent làm node gốc của giao diện mới
import javafx.scene.Parent;

// Import Scene để tạo màn hình mới
import javafx.scene.Scene;

// Import Alert để hiện popup
import javafx.scene.control.Alert;

// Import các control trong giao diện login
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

// Import Button để lấy Stage hiện tại từ nút bấm
import javafx.scene.control.Button;

// Import ActionEvent để lấy thông tin sự kiện click
import javafx.event.ActionEvent;

// Import Stage là cửa sổ hiện tại
import javafx.stage.Stage;

public class LoginController {

    // Liên kết với ô nhập username trong file login-view.fxml
    @FXML
    private TextField usernameField;

    // Liên kết với ô nhập password trong file login-view.fxml
    @FXML
    private PasswordField passwordField;

    // Liên kết với nút Login để lấy Stage hiện tại
    @FXML
    private Button loginButton;

    // Hàm này sẽ được gọi khi người dùng bấm nút Login
    @FXML
    private void handleLogin(ActionEvent event) {
        // Lấy dữ liệu từ ô username
        String username = usernameField.getText();

        // Lấy dữ liệu từ ô password
        String password = passwordField.getText();

        // Kiểm tra nếu người dùng để trống một trong hai ô
        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Lỗi", "Không được để trống!");
        }
        // Kiểm tra tài khoản mẫu để test giao diện
        else if (username.equals("admin") && password.equals("123")) {
            // Nếu đúng thì chuyển sang màn hình Home
            openHomeScreen(event);
        }
        // Nếu sai tài khoản hoặc mật khẩu thì hiện popup lỗi
        else {
            showAlert("Sai", "Sai tài khoản hoặc mật khẩu!");
        }
    }

    // Hàm dùng để mở màn hình Home
    private void openHomeScreen(ActionEvent event) {
        try {
            // Load file home-view.fxml từ resources/views
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/home-view.fxml"));
            Parent root = loader.load();

            // Tạo scene mới cho màn Home
            Scene homeScene = new Scene(root, 500, 350);

            // Lấy cửa sổ hiện tại từ nút Login
            Stage stage = (Stage) loginButton.getScene().getWindow();

            // Đổi scene hiện tại sang màn Home
            stage.setScene(homeScene);

            // Đặt lại tiêu đề cửa sổ
            stage.setTitle("Auction System - Home");

            // Hiển thị cửa sổ
            stage.show();

        } catch (Exception e) {
            // Nếu có lỗi khi load màn hình mới thì in lỗi ra console
            e.printStackTrace();

            // Đồng thời hiện popup báo lỗi
            showAlert("Lỗi", "Không mở được màn hình Home.");
        }
    }

    // Hàm dùng chung để hiện popup thông báo
    private void showAlert(String title, String message) {
        // Tạo popup kiểu thông báo
        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        // Đặt tiêu đề cho popup
        alert.setTitle(title);

        // Không dùng dòng header
        alert.setHeaderText(null);

        // Đặt nội dung popup
        alert.setContentText(message);

        // Hiển thị popup
        alert.showAndWait();
    }
}