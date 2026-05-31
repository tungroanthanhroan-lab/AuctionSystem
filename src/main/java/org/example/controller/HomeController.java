package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.example.model.User;
import org.example.service.AppSession;
import java.io.IOException;
import javafx.scene.control.Button;
import org.example.service.NetworkService;
import javafx.scene.control.TextInputDialog;
import java.util.Optional;
public class HomeController {
    @FXML
    private Button btnMyAuctions;
    @FXML
    private Label welcomeLabel;

    private User currentUser;
    @FXML
    private Button btnCheckBalance;

    @FXML
    private Button btnDeposit;

    @FXML
    private Button btnChangePassword;
    private final NetworkService networkService = new NetworkService();
    public void setUser(User user) {
        this.currentUser = user;
        welcomeLabel.setText("Xin chào, " + user.getUsername() + " (" + user.getRole() + ")");
    }

    /**
     * Khi bấm nút "Xem danh sách đấu giá",
     * chuyển từ màn hình Home sang màn hình danh sách phiên đấu giá.
     */
    @FXML
    private void handleViewAuctions() {
        try {
            // Load màn hình danh sách phiên đấu giá
            FXMLLoader fxmlLoader = new FXMLLoader(
                    getClass().getResource("/views/auction-list-view.fxml")
            );

            Parent root = fxmlLoader.load();

            // Lấy cửa sổ hiện tại từ welcomeLabel
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();

            // Thay scene hiện tại bằng scene danh sách đấu giá
            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Danh sách phiên đấu giá");
            stage.show();

        } catch (IOException e) {
            // Hiện popup nếu không mở được màn hình danh sách
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không thể tải file auction-list-view.fxml");
            e.printStackTrace();
        }
    }
    //hàm đăng xuất account
    /**
     * Đăng xuất tài khoản hiện tại và quay lại màn hình Login.
     */
    @FXML
    private void handleLogout() {
        try {
            // Xóa username đang đăng nhập
            AppSession.clear();

            // Load lại màn hình đăng nhập
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/login-view.fxml")
            );

            Parent root = loader.load();

            // Lấy cửa sổ hiện tại, không tạo cửa sổ mới
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Đăng nhập");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không đăng xuất được!");
            e.printStackTrace();
        }
    }
    @FXML
    private void handleOpenCreateAuction() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/create-auction-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) welcomeLabel.getScene().getWindow();

            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Tạo phiên đấu giá");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không mở được màn hình tạo phiên đấu giá!");
            e.printStackTrace();
        }
    }
    @FXML
    private void handleMyAuctions() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/my-auctions-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) btnMyAuctions.getScene().getWindow();

            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Phiên của tôi");
            stage.show();

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không mở được màn Phiên của tôi!");
            e.printStackTrace();
        }
    }
    /**
     * Hàm hiện popup lỗi.
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    /**
     * Hàm kiểm tra số dư
     */
    @FXML
    private void handleCheckBalance() {
        try {
            String response = networkService.sendMessage("CHECK_BALANCE");

            System.out.println("Server trả về số dư: " + response);

            if (response == null || response.trim().isEmpty()) {
                showAlert(Alert.AlertType.ERROR,
                        "Lỗi",
                        "Server không trả về dữ liệu số dư!");
                return;
            }

            if (response.startsWith("ERROR")) {
                showAlert(Alert.AlertType.ERROR,
                        "Lỗi kết nối",
                        response.replace("ERROR|", ""));
                return;
            }

            if (response.startsWith("FAIL")) {
                showAlert(Alert.AlertType.WARNING,
                        "Không thể kiểm tra số dư",
                        response.replace("FAIL|", ""));
                return;
            }

            if (response.startsWith("BALANCE|")) {
                String balance = response.replace("BALANCE|", "").trim();

                showAlert(Alert.AlertType.INFORMATION,
                        "Số dư tài khoản",
                        "Số dư hiện tại của bạn là: " + balance + " $");
                return;
            }

            showAlert(Alert.AlertType.WARNING,
                    "Phản hồi không xác định",
                    response);

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không thể kiểm tra số dư!");
            e.printStackTrace();
        }
    }
    /**
     * Hàm kiểm tra số dư
     */
    @FXML
    private void handleDeposit() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nạp tiền");
        dialog.setHeaderText(null);
        dialog.setContentText("Nhập số tiền muốn nạp:");

        Optional<String> result = dialog.showAndWait();

        if (result.isEmpty()) {
            return;
        }

        String amountText = result.get().trim();

        if (amountText.isEmpty()) {
            showAlert(Alert.AlertType.WARNING,
                    "Cảnh báo",
                    "Bạn chưa nhập số tiền cần nạp!");
            return;
        }

        try {
            double amount = Double.parseDouble(amountText);

            if (amount <= 0) {
                showAlert(Alert.AlertType.WARNING,
                        "Số tiền không hợp lệ",
                        "Số tiền nạp phải lớn hơn 0!");
                return;
            }

            String response = networkService.sendMessage("DEPOSIT|" + amount);

            System.out.println("Server trả về khi nạp tiền: " + response);

            if (response == null || response.trim().isEmpty()) {
                showAlert(Alert.AlertType.ERROR,
                        "Lỗi",
                        "Server không trả về phản hồi!");
                return;
            }

            if (response.startsWith("ERROR")) {
                showAlert(Alert.AlertType.ERROR,
                        "Lỗi kết nối",
                        response.replace("ERROR|", ""));
                return;
            }

            if (response.startsWith("FAIL")) {
                showAlert(Alert.AlertType.WARNING,
                        "Nạp tiền thất bại",
                        response.replace("FAIL|", ""));
                return;
            }

            if (response.startsWith("SUCCESS")) {
                showAlert(Alert.AlertType.INFORMATION,
                        "Nạp tiền thành công",
                        response.replace("SUCCESS|", ""));

                /*
                 * Sau khi nạp thành công, hỏi lại số dư để user thấy thay đổi.
                 */
                handleCheckBalance();
                return;
            }

            showAlert(Alert.AlertType.WARNING,
                    "Phản hồi không xác định",
                    response);

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi nhập liệu",
                    "Vui lòng nhập số tiền hợp lệ, ví dụ: 1000");
        }
    }
    @FXML
    private void handleChangePassword() {
        TextInputDialog oldPassDialog = new TextInputDialog();
        oldPassDialog.setTitle("Đổi mật khẩu");
        oldPassDialog.setHeaderText(null);
        oldPassDialog.setContentText("Nhập mật khẩu cũ:");

        Optional<String> oldPassResult = oldPassDialog.showAndWait();

        if (oldPassResult.isEmpty()) {
            return;
        }

        String oldPassword = oldPassResult.get().trim();

        if (oldPassword.isEmpty()) {
            showAlert(Alert.AlertType.WARNING,
                    "Cảnh báo",
                    "Bạn chưa nhập mật khẩu cũ!");
            return;
        }

        TextInputDialog newPassDialog = new TextInputDialog();
        newPassDialog.setTitle("Đổi mật khẩu");
        newPassDialog.setHeaderText(null);
        newPassDialog.setContentText("Nhập mật khẩu mới:");

        Optional<String> newPassResult = newPassDialog.showAndWait();

        if (newPassResult.isEmpty()) {
            return;
        }

        String newPassword = newPassResult.get().trim();

        if (newPassword.isEmpty()) {
            showAlert(Alert.AlertType.WARNING,
                    "Cảnh báo",
                    "Bạn chưa nhập mật khẩu mới!");
            return;
        }

        if (newPassword.equals(oldPassword)) {
            showAlert(Alert.AlertType.WARNING,
                    "Mật khẩu không thay đổi",
                    "Mật khẩu mới không được trùng mật khẩu cũ!");
            return;
        }

        try {
            String message = "CHANGE_PASSWORD|" + oldPassword + "|" + newPassword;
            String response = networkService.sendMessage(message);

            System.out.println("Server trả về khi đổi mật khẩu: " + response);

            if (response == null || response.trim().isEmpty()) {
                showAlert(Alert.AlertType.ERROR,
                        "Lỗi",
                        "Server không trả về phản hồi!");
                return;
            }

            if (response.startsWith("ERROR")) {
                showAlert(Alert.AlertType.ERROR,
                        "Lỗi kết nối",
                        response.replace("ERROR|", ""));
                return;
            }

            if (response.startsWith("FAIL")) {
                showAlert(Alert.AlertType.WARNING,
                        "Đổi mật khẩu thất bại",
                        response.replace("FAIL|", ""));
                return;
            }

            if (response.startsWith("SUCCESS")) {
                showAlert(Alert.AlertType.INFORMATION,
                        "Đổi mật khẩu thành công",
                        response.replace("SUCCESS|", ""));
                return;
            }

            showAlert(Alert.AlertType.WARNING,
                    "Phản hồi không xác định",
                    response);

        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không thể đổi mật khẩu!");
            e.printStackTrace();
        }
    }
}