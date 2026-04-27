package org.example.controller;

import javafx.fxml.FXMLLoader;
import org.example.client.AuctionClient;
import javafx.scene.input.KeyCode;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class LoginController {

    // Ô nhập tài khoản trong file login-view.fxml.
    @FXML
    private TextField txtUsername;

    // Ô nhập mật khẩu trong file login-view.fxml.
    @FXML
    private PasswordField txtPassword;

    // Đối tượng client dùng để gửi request sang server.
    private AuctionClient auctionClient = new AuctionClient();

    /**
     * Hàm này chạy khi người dùng bấm nút Đăng nhập.
     */
    @FXML
    public void handleLogin() {
        // Lấy dữ liệu người dùng nhập từ giao diện.
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        // Bắt lỗi chưa nhập tài khoản.
        if (username == null || username.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Bạn chưa nhập tài khoản!");
            return;
        }

        // Bắt lỗi chưa nhập mật khẩu.
        if (password == null || password.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Bạn chưa nhập mật khẩu!");
            return;
        }

        // Gửi tài khoản và mật khẩu sang server để kiểm tra.
        String response = auctionClient.login(username.trim(), password.trim());

        System.out.println("Server trả về: " + response);

        // Nếu server báo đăng nhập thành công.
        if (response.startsWith("LOGIN_SUCCESS")) {
            showAlert(Alert.AlertType.INFORMATION, "Đăng nhập thành công", "Chào mừng " + username);
            openHomeView();

        } else if (response.startsWith("LOGIN_FAILED")) {
            // Tách thông báo lỗi từ server.
            String[] parts = response.split("\\|", 2);
            String message = parts.length > 1 ? parts[1] : "Đăng nhập thất bại";

            showAlert(Alert.AlertType.ERROR, "Đăng nhập thất bại", message);

        } else if (response.startsWith("ERROR")) {
            // Lỗi thường gặp: chưa bật server, sai port, mất kết nối.
            String[] parts = response.split("\\|", 2);
            String message = parts.length > 1 ? parts[1] : "Lỗi không xác định";

            showAlert(Alert.AlertType.ERROR, "Lỗi kết nối", message);

        } else {
            // Trường hợp server trả về dữ liệu không đúng format.
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Server trả về phản hồi không hợp lệ");
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