package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.service.NetworkService;

import java.io.IOException;

public class RegisterController {

    @FXML
    private TextField txtUsername;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private PasswordField txtConfirmPassword;

    @FXML
    private ComboBox<String> cbRole;

    private NetworkService networkService = new NetworkService();

    @FXML
    public void initialize() {
        cbRole.getItems().addAll("BIDDER", "SELLER");
    }

    @FXML
    private void handleRegister() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();
        String confirmPassword = txtConfirmPassword.getText();
        String role = cbRole.getValue();

        if (username == null || username.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Bạn chưa nhập tài khoản!");
            return;
        }

        if (password == null || password.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Bạn chưa nhập mật khẩu!");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Mật khẩu nhập lại không khớp!");
            return;
        }

        if (role == null) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Bạn chưa chọn vai trò!");
            return;
        }

        String message = "REGISTER|" + username.trim() + "|" + password.trim() + "|" + role;
        String response = networkService.sendMessage(message);

        if (response.startsWith("ERROR")) {
            showAlert(Alert.AlertType.ERROR, "Lỗi kết nối", "Không kết nối được tới server!");
            return;
        }

        showAlert(Alert.AlertType.INFORMATION,
                "Server phản hồi",
                response);

        handleBackToLogin();
    }

    @FXML
    private void handleBackToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/login-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) txtUsername.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Đăng nhập");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không quay lại được màn hình đăng nhập!");
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