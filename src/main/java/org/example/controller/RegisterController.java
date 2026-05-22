package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.example.service.NetworkService;

import java.io.IOException;

public class RegisterController {
    // =========================
    // 1. Các thành phần giao diện
    // =========================

    // Ô nhập tên tài khoản
    @FXML
    private TextField txtUsername;

    // ô nhập mật khẩu
    @FXML
    private PasswordField txtPassword;

    //ô nhập lại mật khẩu
    @FXML
    private PasswordField txtConfirmPassword;

    // Nút đăng ký
    @FXML
    private Button btnRegister;

    // Nút quay lại màn hình đăng nhập
    @FXML
    private Button btnBackToLogin;

    // =========================
    // 2. Service hỗ trợ
    // =========================

    /*
     * NetworkService dùng để gửi request đăng ký lên server.
     * Message gửi đi có dạng:
     * REGISTER|username|password|role
     */
    private final NetworkService networkService = new NetworkService();

    // =========================
    // 3. Khởi tạo màn hình
    // =========================

    /*
     * initialize() tự động chạy sau khi register-view.fxml được load.
     * Ở đây ta:
     * - Cài đặt điều hướng bằng phím mũi tên và Enter
     */
    @FXML
    public void initialize() {
        // Ô username: xuống thì sang ô password
        txtUsername.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                txtPassword.requestFocus();
                event.consume();
            }
        });

        // Ô password: lên thì về username, xuống thì sang ô nhập lại mật khẩu
        txtPassword.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                txtUsername.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.DOWN) {
                txtConfirmPassword.requestFocus();
                event.consume();
            }
        });

        // Ô nhập lại mật khẩu: lên thì về password, xuống thì sang nút đăng ký, Enter thì đăng ký
        txtConfirmPassword.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                txtPassword.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.DOWN) {
                btnRegister.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.ENTER) {
                handleRegister();
                event.consume();
            }
        });

        // Nút Đăng ký: lên thì về ô nhập lại mật khẩu, xuống thì sang nút quay lại, Enter thì đăng ký
        btnRegister.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                txtConfirmPassword.requestFocus();
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

    // =========================
    // 4. Xử lý đăng ký
    // =========================

    /*
     * Hàm này chạy khi người dùng bấm nút Đăng ký.
     *
     * Luồng xử lý:
     * 1. Lấy dữ liệu từ giao diện.
     * 2. Kiểm tra dữ liệu hợp lệ.
     * 3. Gửi REGISTER lên server qua NetworkService.
     * 4. Nếu server phản hồi thành công thì quay lại màn Login.
     */
    @FXML
    private void handleRegister() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();
        String confirmPassword = txtConfirmPassword.getText();

        /*
         * Vì màn đăng ký không còn chọn role nữa,
         * ta để mặc định tài khoản có cả quyền mua và bán.
         */
        String role = "USER";

        if (username == null || username.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Bạn chưa nhập tài khoản!");
            return;
        }

        if (password == null || password.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Bạn chưa nhập mật khẩu!");
            return;
        }

        if (confirmPassword == null || confirmPassword.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Bạn chưa nhập lại mật khẩu!");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Mật khẩu nhập lại không khớp!");
            return;
        }

        String message = "REGISTER|" + username.trim() + "|" + password.trim() + "|" + role;
        String response = networkService.sendMessage(message);

        if (response.startsWith("ERROR")) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi kết nối",
                    "Không kết nối được tới server!");
            return;
        }

        if (response.startsWith("SUCCESS")) {
            showAlert(Alert.AlertType.INFORMATION,
                    "Đăng ký thành công",
                    layNoiDungPhanHoi(response));

            handleBackToLogin();
            return;
        }

        if (response.startsWith("FAIL")) {
            showAlert(Alert.AlertType.ERROR,
                    "Đăng ký thất bại",
                    layNoiDungPhanHoi(response));
            return;
        }

        showAlert(Alert.AlertType.WARNING,
                "Phản hồi không xác định",
                response);
    }

    @FXML
    private void handleBackToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/login-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) txtUsername.getScene().getWindow();
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

    /*
     * Tách nội dung sau dấu | trong phản hồi từ server.
     * Ví dụ:
     * SUCCESS|Đăng ký thành công
     * -> Đăng ký thành công
     */
    private String layNoiDungPhanHoi(String response) {
        String[] parts = response.split("\\|", 2);

        if (parts.length == 2) {
            return parts[1];
        }

        return response;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}