package org.example.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.input.KeyCode;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import org.example.service.NetworkService;

public class LoginController {

    // Ô nhập tài khoản trong file login-view.fxml.
    @FXML
    private TextField txtUsername;

    // Ô nhập mật khẩu trong file login-view.fxml.
    @FXML
    private PasswordField txtPassword;

    // Đối tượng client dùng để gửi request sang server.
    private NetworkService networkService = new NetworkService();

    /**
     * Hàm này chạy khi người dùng bấm nút Đăng nhập.
     */
    @FXML
    public void handleLogin() {
        // Lấy dữ liệu người dùng nhập từ giao diện
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        // Bắt lỗi chưa nhập tài khoản
        if (username == null || username.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Bạn chưa nhập tài khoản!");
            return;
        }

        // Bắt lỗi chưa nhập mật khẩu
        if (password == null || password.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Bạn chưa nhập mật khẩu!");
            return;
        }

        // Tạo message gửi lên server
        // Server hiện tại nhận text thường nên ta gửi dạng LOGIN|username|password
        String message = "LOGIN|" + username.trim() + "|" + password.trim();

        // Gửi message sang server thông qua NetworkService
        String response = networkService.sendMessage(message);

        System.out.println("Server trả về: " + response);

        // Nếu không kết nối được server
        if (response.startsWith("ERROR")) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi kết nối",
                    "Không kết nối được tới server!");
            return;
        }

        // Vì server hiện tại chỉ trả phản hồi chung chung,
        // tạm thời coi như gửi thành công thì cho vào Home
        showAlert(Alert.AlertType.INFORMATION,
                "Server phản hồi",
                response);

        openHomeView();
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

    /**
     * Mở màn hình Home sau khi đăng nhập thành công.
     */
    private void openHomeView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/home-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) txtUsername.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Auction System - Home");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không mở được màn hình Home!");
            e.printStackTrace();
        }
    }
    @FXML
    public void initialize() {
        txtUsername.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                txtPassword.requestFocus();
            }
        });

        txtPassword.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                txtUsername.requestFocus();
            }
        });
    }
}