package org.example.controller;

import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
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
    private Button btnRegister;

    @FXML
    private Button btnBackToLogin;

    @FXML
    public void initialize() {
        // Thêm các vai trò cho ComboBox
        cbRole.getItems().addAll("BIDDER", "SELLER");

        // Username: xuống hoặc Enter thì sang ô mật khẩu
        txtUsername.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.ENTER) {
                txtPassword.requestFocus();
                event.consume();
            }
        });

        // Password: lên thì về username, xuống hoặc Enter thì sang confirm password
        txtPassword.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                txtUsername.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.ENTER) {
                txtConfirmPassword.requestFocus();
                event.consume();
            }
        });

        // Confirm password: lên thì về password, xuống hoặc Enter thì sang chọn role
        txtConfirmPassword.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                txtPassword.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.ENTER) {
                cbRole.requestFocus();
                event.consume();
            }
        });
        // Click chuột vào ComboBox thì xổ danh sách lựa chọn
        cbRole.setOnMouseClicked(event -> {
            cbRole.show();
        });

        // Role:
// - Enter: xổ danh sách lựa chọn BIDDER / SELLER
// - Nếu đã chọn role rồi:
//   + Mũi tên xuống chuyển sang nút Đăng ký
//   + Mũi tên lên quay lại ô Nhập lại mật khẩu
// - Không để mũi tên xuống tự đổi BIDDER thành SELLER
        cbRole.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                cbRole.show();
                event.consume();

            } else if (event.getCode() == KeyCode.DOWN) {
                if (cbRole.getValue() == null) {
                    cbRole.show();
                } else {
                    btnRegister.requestFocus();
                }
                event.consume();

            } else if (event.getCode() == KeyCode.UP) {
                if (cbRole.getValue() == null) {
                    cbRole.show();
                } else {
                    txtConfirmPassword.requestFocus();
                }
                event.consume();
            }
        });

        // Nút Đăng ký: lên thì về role, xuống thì sang nút quay lại, Enter thì đăng ký
        btnRegister.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                cbRole.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.DOWN) {
                btnBackToLogin.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.ENTER) {
                handleRegister();
                event.consume();
            }
        });

        // Nút Quay lại: lên thì về nút Đăng ký, Enter thì quay lại Login
        btnBackToLogin.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                btnRegister.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.ENTER) {
                handleBackToLogin();
                event.consume();
            }
        });
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