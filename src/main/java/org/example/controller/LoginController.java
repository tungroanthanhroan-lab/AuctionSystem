package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.example.service.AppSession;
import org.example.service.NetworkService;

import java.io.IOException;
public class LoginController {


    //=====================
    //1.Các thành phần giao diện
    //=====================

    // Ô nhập tài khoản trong file login-view.fxml.
    @FXML
    private TextField txtUsername;
    // Ô nhập mật khẩu trong file login-view.fxml.
    @FXML
    private PasswordField txtPassword;

    //=====================
    //2.Service hỗ trợ
    //=====================

    /*
     *NetworkService dùng để gửi message từ UI lên server.
     *Ví dụ: LOGIN|username|password
     */
    private final NetworkService networkService = new NetworkService();


    //=====================
    //3.Hàm khởi tạo màn hình
    //=====================
    /*
     *initialize() tự động chạy sau khi file login-view.fxml được load.
     *ở đây dùng để thiết lập điều hướng bằng bàn phím
     */
    @FXML
    public void initialize() {
        //ở ô username, bấm mũi tên xuống thì chuyển sang ô password
        txtUsername.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                txtPassword.requestFocus();
                event.consume();
            }
        });
        //ở ô password, bấm mũi tên lên thì quay lại ô username
        txtPassword.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                txtUsername.requestFocus();
                event.consume();
            }
        });
    }


    //=====================
    //4.Xử lí đăng nhập
    //=====================
    /*
     *Hàm này chạy khi người dùng bấm nút đăng nhập.
     *
     * Luồng xử lí:
     * 1. Lấy username/password từ giao diện.
     * 2. Kiểm tra người dùng có nhập đủ chưa.
     * 3.Gửi LOGIN lên server qua NetworkService.
     * 4. Nếu server phản hồi thành công thì lưu username vào AppSession.
     * 5. Chuyển sang màn hình Home.
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

        /*
         *Tạo message gửi lên server.
         *đây là protocol tạm của nhóm:
         * LOGIN|username|password
         */
        String message = "LOGIN|" + username.trim() + "|" + password.trim();

        // Gửi message sang server thông qua NetworkService
        String response = networkService.sendMessage(message);

        System.out.println("Server trả về: " + response);

        // Nếu không kết nối được server thì báo lỗi và không cho vào Home
        if (response.startsWith("ERROR")) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi kết nối",
                    "Không kết nối được tới server!");
            return;
        }

// Nếu server trả SUCCESS nghĩa là đăng nhập đúng tài khoản/mật khẩu trong database
        if (response.startsWith("SUCCESS")) {
            /*
             * Lưu username đang đăng nhập.
             * Các màn sau như BiddingController sẽ dùng username này
             * để hiển thị người đặt giá.
             */
            AppSession.setCurrentUsername(username.trim());

            showAlert(Alert.AlertType.INFORMATION,
                    "Đăng nhập thành công",
                    layNoiDungPhanHoi(response));

            if (response.toUpperCase().contains("SELLER")) {
                System.out.println("Dang mo man hinh Seller Home...");
                openSellerHomeView();
            } else {
                System.out.println("Dang mo man hinh Home...");
                openHomeView();
            }

            return;
        }

// Nếu server trả FAIL nghĩa là sai tài khoản/mật khẩu hoặc sai cú pháp
        if (response.startsWith("FAIL")) {
            showAlert(Alert.AlertType.ERROR,
                    "Đăng nhập thất bại",
                    layNoiDungPhanHoi(response));
            return;
        }

// Trường hợp server trả về nội dung không nằm trong format dự kiến
        showAlert(Alert.AlertType.WARNING,
                "Phản hồi không xác định",
                response);
    }
    //======================
    //5.Mở màn hình đăng kí
    //======================

    /*
     *Hàm này chạy khi người dùng bấm nút đăng kí
     * Chuyển ừ login-view.fxml sang register-view.fxml.
     */
    @FXML
    private void handleOpenRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/register-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) txtUsername.getScene().getWindow();
            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Đăng ký tài khoản");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không mở được màn hình đăng ký!");
            e.printStackTrace();
        }
    }
    //======================
    //6. Mở màn hình home
    //======================

    /*
     * Mở màn hình Home sau khi đăng nhập thành công.
     */
    private void openHomeView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/home-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) txtUsername.getScene().getWindow();
            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Auction System - Home");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Không mở được màn hình Home!");
            e.printStackTrace();
        }
    }
        /*
         * Tách nội dung sau dấu | trong phản hồi từ server.
         * Ví dụ:
         * SUCCESS|Đăng nhập thành công
         * -> Đăng nhập thành công
         */
        private String layNoiDungPhanHoi(String response) {
            String[] parts = response.split("\\|", 2);

            if (parts.length == 2) {
                return parts[1];
            }

            return response;
        }
    //======================
    //7.mở màn seller home
    //======================
    /*
     * Mở màn hình riêng cho Seller sau khi đăng nhập thành công.
     */
    private void openSellerHomeView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/seller-home-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) txtUsername.getScene().getWindow();
            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Trang người bán");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không mở được màn hình người bán!");
            e.printStackTrace();
        }
    }

    //======================
    //8.Hàm dùng chung
    //======================

    /*
     * Hàm dùng chung để hiện popup thông báo.
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}