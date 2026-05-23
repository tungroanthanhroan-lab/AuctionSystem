package org.example.controller;

import org.example.service.AuctionDataStore;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;
import javafx.scene.control.ListView;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.application.Platform;
import org.example.service.NetworkService;
import org.example.service.AppSession;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;


import java.time.LocalDateTime;
public class BiddingController {
    //khai bao cac thanh phan co fx:id ben file FXML
    @FXML
    private Label lblTenSanPham;

    @FXML
    private Label lblGiaHienTai;

    @FXML
    private Label lblNguoiDanDau;

    @FXML
    private TextField txtNhapGia;

    @FXML
    private ListView<String> listLichSuBid;

    @FXML
    private Button btnDatGia;

    @FXML
    private Button btnQuayLai;
    @FXML
    private Label lblCountdown;

    private Timeline countdownTimeline;
    private LocalDateTime auctionEndTime;
    private Timeline refreshTimeline;

    // Tên phiên đấu giá hiện tại đang được mở

    private String auctionName;
    private String currentAuctionId;

    @FXML
    private Label lblTrangThai;

    @FXML
    private Button btnDongPhien;
    private boolean phienDangMo = true;
    // Dùng để gửi dữ liệu từ màn hình đấu giá lên server
    private NetworkService networkService = new NetworkService();
    //tam
    private double giaHienTai = 0.0;

    @FXML
    public void setAuctionInfo(String auctionInfo) {
        try {
            /*
             * auctionInfo có thể có dạng:
             * "2 - Laptop Gaming - Giá hiện tại: 1000.0$ - Trạng thái: OPEN"
             *
             * Hoặc dạng cũ:
             * "2 - Laptop Gaming - Giá hiện tại: 1000.0$"
             *
             * Mục tiêu:
             * 1. Lấy tên phiên:
             *    "2 - Laptop Gaming"
             *
             * 2. Lấy giá hiện tại:
             *    1000.0
             *
             * 3. Lấy trạng thái nếu có:
             *    OPEN / RUNNING / FINISHED / CLOSED
             */

            // Lưu toàn bộ chuỗi gốc để các hàm khác vẫn có thể dùng nếu cần
            String originalAuctionInfo = auctionInfo;

            /*
             * Tách chuỗi để lấy phần tên phiên.
             * Ví dụ:
             * "2 - Laptop Gaming - Giá hiện tại: 1000.0$ - Trạng thái: OPEN"
             * mainParts[0] = "2 - Laptop Gaming"
             */
            String[] mainParts = auctionInfo.split(" - Giá hiện tại:");
            auctionName = mainParts[0].trim();

            /*
             * auctionName có dạng:
             * "2 - Laptop Gaming"
             *
             * Lấy auctionId để refresh đúng phiên từ server.
             */
            String[] auctionNameParts = auctionName.split(" - ", 2);
            currentAuctionId = auctionNameParts[0].trim();

            // Hiển thị tên phiên
            lblTenSanPham.setText("Sản phẩm: " + auctionName);

            /*
             * Load giá hiện tại từ chuỗi auctionInfo do AuctionList truyền sang.
             * Không dùng AuctionDataStore.getPrice() nữa vì dữ liệu thật đang lấy từ server.
             */
            giaHienTai = 0.0;

            if (mainParts.length > 1) {
                String priceAndStatusPart = mainParts[1];

                /*
                 * priceAndStatusPart ví dụ:
                 * " 1000.0$ - Trạng thái: OPEN"
                 *
                 * Tách tiếp theo dấu "$" để lấy phần giá.
                 */
                String priceText = priceAndStatusPart.split("\\$")[0].trim();
                giaHienTai = Double.parseDouble(priceText);
            }

            lblGiaHienTai.setText("Giá hiện tại: " + giaHienTai + " $");

            /*
             * Load người dẫn đầu.
             *
             * Hiện tại VIEW_ITEMS chưa trả current_leader,
             * nên tạm hiển thị từ AuctionDataStore nếu có.
             * Nếu không có dữ liệu local thì hiển thị "Chưa có".
             */
            String highestBidder = AuctionDataStore.getHighestBidder(auctionName);

            if (highestBidder == null || highestBidder.trim().isEmpty()) {
                highestBidder = "Chưa có";
            }

            lblNguoiDanDau.setText("Người dẫn đầu: " + highestBidder);

            /*
             * Load lịch sử bid.
             *
             * Hiện tại backend chưa có GET_BID_HISTORY cho UI,
             * nên phần này vẫn tạm đọc lịch sử local để không làm crash giao diện.
             */
            listLichSuBid.getItems().clear();
            listLichSuBid.getItems().addAll(
                    AuctionDataStore.getBidHistory(auctionName)
            );

            /*
             * Load trạng thái phiên.
             *
             * Ưu tiên đọc trạng thái từ chuỗi server trả:
             * "Trạng thái: OPEN"
             *
             * Nếu không có trạng thái trong chuỗi thì fallback về AuctionDataStore.
             */
            /*
             * Load trạng thái phiên và thời gian kết thúc.
             *
             * auctionInfo có dạng:
             * "1 - hee loo - Giá hiện tại: 2.0$ - Trạng thái: OPEN - Kết thúc: 2026-05-30T23:59"
             */
            String status = null;
            String endTimeText = null;

            /*
             * Lấy endTime trước.
             */
            if (originalAuctionInfo.contains("Kết thúc:")) {
                endTimeText = originalAuctionInfo
                        .substring(originalAuctionInfo.indexOf("Kết thúc:") + "Kết thúc:".length())
                        .trim();
            }

            /*
             * Lấy status.
             * Nếu sau status có phần " - Kết thúc:" thì cắt bỏ phần đó.
             */
            if (originalAuctionInfo.contains("Trạng thái:")) {
                status = originalAuctionInfo
                        .substring(originalAuctionInfo.indexOf("Trạng thái:") + "Trạng thái:".length())
                        .trim();

                if (status.contains(" - Kết thúc:")) {
                    status = status.substring(0, status.indexOf(" - Kết thúc:")).trim();
                }
            }

            /*
             * Xử lý trạng thái.
             */
            if (status != null && !status.isEmpty()) {
                phienDangMo = status.equalsIgnoreCase("OPEN")
                        || status.equalsIgnoreCase("RUNNING")
                        || status.equalsIgnoreCase("ĐANG MỞ");

                if (phienDangMo) {
                    lblTrangThai.setText("Trạng thái: ĐANG MỞ");
                    lblTrangThai.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                } else {
                    lblTrangThai.setText("Trạng thái: ĐÃ KẾT THÚC");
                    lblTrangThai.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                }

            } else {
                /*
                 * Fallback cho dữ liệu demo cũ nếu chuỗi không có trạng thái.
                 */
                phienDangMo = AuctionDataStore.isAuctionOpen(auctionName);

                if (phienDangMo) {
                    lblTrangThai.setText("Trạng thái: ĐANG MỞ");
                    lblTrangThai.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                } else {
                    lblTrangThai.setText("Trạng thái: ĐÃ KẾT THÚC");
                    lblTrangThai.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                }
            }

            /*
             * Xử lý countdown.
             */
            if (endTimeText != null && !endTimeText.isEmpty()) {
                try {
                    auctionEndTime = LocalDateTime.parse(endTimeText);
                    startCountdown();
                } catch (Exception parseException) {
                    lblCountdown.setText("Thời gian còn lại: Không xác định");
                    System.out.println("Không parse được endTime: " + endTimeText);
                    parseException.printStackTrace();
                }
            } else {
                lblCountdown.setText("Thời gian còn lại: Không xác định");
                System.out.println("auctionInfo không có endTime: " + originalAuctionInfo);
            }

            /*
             * Bật/tắt các nút theo trạng thái phiên.
             */
            txtNhapGia.setDisable(!phienDangMo);
            btnDatGia.setDisable(!phienDangMo);
            /*
             * Tạm thời cho cả ADMIN và USER thấy nút Đóng phiên.
             * Backend sẽ kiểm tra quyền thật:
             * - ADMIN đóng mọi phiên
             * - USER chỉ đóng phiên do chính mình tạo
             */
            btnDongPhien.setVisible(true);
            btnDongPhien.setManaged(true);
            btnDongPhien.setDisable(!phienDangMo);

        } catch (Exception e) {
            /*
             * Nếu chuỗi truyền vào lỗi format,
             * vẫn hiển thị thông tin cơ bản để tránh crash.
             */
            auctionName = auctionInfo;
            lblTenSanPham.setText("Sản phẩm: " + auctionInfo);

            giaHienTai = 0.0;
            lblGiaHienTai.setText("Giá hiện tại: 0 $");

            lblNguoiDanDau.setText("Người dẫn đầu: Chưa có");
            listLichSuBid.getItems().clear();

            phienDangMo = true;
            lblTrangThai.setText("Trạng thái: ĐANG MỞ");
            lblTrangThai.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

            txtNhapGia.setDisable(false);
            btnDatGia.setDisable(false);
            btnDongPhien.setVisible(true);
            btnDongPhien.setManaged(true);
            btnDongPhien.setDisable(false);

            e.printStackTrace();
        }
    }
    @FXML
    public void handleDatGia() {
        String inputStr = txtNhapGia.getText();

        // Nếu phiên đã đóng thì không cho đặt giá nữa
        if (!phienDangMo) {
            hienThiPopup(Alert.AlertType.ERROR,
                    "Phiên đã đóng",
                    "Phiên đấu giá đã kết thúc, bạn không thể đặt giá nữa!");
            return;
        }

        // Kiểm tra nếu không nhập gì hoặc nhập toàn dấu cách mà vẫn bấm nút đặt giá
        if (inputStr == null || inputStr.trim().isEmpty()) {
            hienThiPopup(Alert.AlertType.WARNING,
                    "Cảnh báo",
                    "Bạn chưa đặt giá tiền!");
            return;
        }

        try {
            double giaDat = Double.parseDouble(inputStr.trim());

            // Bắt lỗi đặt giá thấp hơn hoặc bằng giá hiện tại
            if (giaDat <= giaHienTai) {
                hienThiPopup(Alert.AlertType.ERROR,
                        "Lỗi đặt giá",
                        "Phải đặt giá cao hơn " + giaHienTai + "$");
                return;
            }

            String username = AppSession.getCurrentUsername();

            /*
             * Backend mới cần format:
             * BID|auctionId|amount
             *
             * Tạm thời auctionName đang có dạng:
             * "2 - Laptop Gaming"
             * nên lấy auctionId bằng phần trước dấu " - "
             */
            String auctionId = auctionName.split(" - ")[0].trim();

            String message = "BID|" + auctionId + "|" + giaDat;

            // Gửi giá đặt lên server thông qua NetworkService
            String response = networkService.sendMessage(message);

            System.out.println("Server trả về: " + response);

            // Nếu không kết nối được server thì báo lỗi
            if (response.startsWith("ERROR")) {
                hienThiPopup(Alert.AlertType.ERROR,
                        "Lỗi kết nối",
                        response.replace("ERROR|", ""));
                return;
            }
            /*
             * Nếu server từ chối bid thì KHÔNG cập nhật UI.
             * Tránh lỗi popup báo FAIL nhưng giá vẫn đổi.
             */
            if (response.startsWith("FAIL")) {
                /*
                 * Nếu server từ chối bid, rất có thể giá hiện tại trên server
                 * đã cao hơn giá UI đang hiển thị.
                 * Vì vậy refresh lại phiên hiện tại ngay để UI bắt kịp server.
                 */
                refreshCurrentAuctionFromServer();

                hienThiPopup(Alert.AlertType.WARNING,
                        "Đặt giá thất bại",
                        response.replace("FAIL|", ""));

                return;
            }
            /*
             * Chỉ khi server trả SUCCESS mới cập nhật:
             * - giá hiện tại
             * - người dẫn đầu
             * - lịch sử đặt giá
             */
            if (response.startsWith("SUCCESS")) {
                giaHienTai = giaDat;

                AuctionDataStore.updateBid(auctionName, giaDat, username);

                lblGiaHienTai.setText("Giá hiện tại: " + giaHienTai + "$");
                lblNguoiDanDau.setText("Người dẫn đầu: " + username);

                listLichSuBid.getItems().clear();
                listLichSuBid.getItems().addAll(
                        AuctionDataStore.getBidHistory(auctionName)
                );

                txtNhapGia.clear();

                hienThiPopup(Alert.AlertType.INFORMATION,
                        "Đặt giá thành công",
                        "Bạn đã đặt giá " + giaDat + " $ thành công!");

                return;
            }

            // Trường hợp server trả response lạ
            hienThiPopup(Alert.AlertType.WARNING,
                    "Phản hồi không xác định",
                    response);

        } catch (NumberFormatException e) {
            // Lỗi nhập liệu của người dùng: chữ, ký tự, v.v.
            hienThiPopup(Alert.AlertType.ERROR,
                    "Lỗi nhập liệu",
                    "Vui lòng chỉ nhập số (ví dụ: 1000), không nhập chữ!");
        }
    }
    //----chuẩn bị cho realtime----
    //phần này được gọi khi có người đặt giá cao hơn
    public void nhanGiaMoiTuServer(double giaMoi) {
        this.giaHienTai = giaMoi;
        capNhatGiaTrenUI(giaMoi);
    }
    //Platform.runlater để tránh crash khi luồng ngầm đổi giao diện
    private void capNhatGiaTrenUI(double giaMoi) {
        Platform.runLater(() -> {
            lblGiaHienTai.setText("Giá hiện tại: " + giaMoi + " $");
        });
    }
    private void startCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        countdownTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), event -> updateCountdown())
        );

        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();

        updateCountdown();
    }

    private void updateCountdown() {
        if (auctionEndTime == null) {
            lblCountdown.setText("Thời gian còn lại: Không xác định");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        java.time.Duration remaining = java.time.Duration.between(now, auctionEndTime);

        long seconds = remaining.getSeconds();

        if (seconds <= 0) {
            lblCountdown.setText("Thời gian còn lại: 00:00:00");

            phienDangMo = false;

            lblTrangThai.setText("Trạng thái: ĐÃ KẾT THÚC");
            lblTrangThai.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");

            txtNhapGia.setDisable(true);
            btnDatGia.setDisable(true);
            btnDongPhien.setDisable(true);

            if (countdownTimeline != null) {
                countdownTimeline.stop();
            }

            return;
        }

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        lblCountdown.setText(
                String.format("Thời gian còn lại: %02d:%02d:%02d", hours, minutes, secs)
        );
    }
    private void startAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }

        refreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(2), event -> {
                    /*
                     * Chạy network ở thread riêng để không làm đơ giao diện JavaFX.
                     */
                    new Thread(() -> refreshCurrentAuctionFromServer()).start();
                })
        );

        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }
    private void refreshCurrentAuctionFromServer() {
        try {
            if (currentAuctionId == null || currentAuctionId.trim().isEmpty()) {
                return;
            }

            String response = networkService.sendMessage("VIEW_ITEMS");

            if (response == null || !response.startsWith("AUCTIONS")) {
                return;
            }

            String[] parts = response.split("\\|");

            for (int i = 1; i < parts.length; i++) {
                String auctionData = parts[i];
                String[] fields = auctionData.split(",", -1);

                /*
                 * Format hiện tại:
                 * auctionId,title,currentHighestBid,status,endTime
                 * hoặc auctionId,title,currentHighestBid,status,startTime,endTime
                 */
                if (fields.length < 4) {
                    continue;
                }

                String auctionId = fields[0].trim();

                if (!auctionId.equals(currentAuctionId)) {
                    continue;
                }

                String title = fields[1].trim();
                double serverPrice = Double.parseDouble(fields[2].trim());
                String status = fields[3].trim();

                Platform.runLater(() -> {
                    /*
                     * Nếu giá server khác giá UI đang hiển thị
                     * thì cập nhật màn hình.
                     */
                    if (serverPrice != giaHienTai) {
                        giaHienTai = serverPrice;

                        lblGiaHienTai.setText("Giá hiện tại: " + giaHienTai + " $");

                        /*
                         * Backend hiện chưa trả currentLeader trong VIEW_ITEMS,
                         * nên UI chưa biết chính xác ai đang dẫn đầu.
                         */
                        lblNguoiDanDau.setText("Người dẫn đầu: Đã cập nhật từ server");
                    }

                    boolean serverAuctionOpen = status.equalsIgnoreCase("OPEN")
                            || status.equalsIgnoreCase("RUNNING");

                    if (!serverAuctionOpen && phienDangMo) {
                        phienDangMo = false;

                        lblTrangThai.setText("Trạng thái: ĐÃ KẾT THÚC");
                        lblTrangThai.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");

                        txtNhapGia.setDisable(true);
                        btnDatGia.setDisable(true);
                        btnDongPhien.setDisable(true);
                    }
                });

                return;
            }

        } catch (Exception e) {
            /*
             * Không popup ở auto refresh, tránh cứ 2 giây hiện lỗi.
             */
            System.out.println("[AutoRefresh] Không refresh được phiên hiện tại: " + e.getMessage());
        }
    }
    //hàm hỗ trợ bật Popup
    private void hienThiPopup(Alert.AlertType loaiPopup, String tieuDe, String noiDung){
        Alert alert = new Alert(loaiPopup);
        alert.setTitle(tieuDe);
        alert.setHeaderText(null);
        alert.setContentText(noiDung);
        alert.showAndWait();
    }
    /**
     * Hàm này chạy khi người dùng bấm nút "QUAY LẠI DANH SÁCH".
     * Nhiệm vụ:
     * - Load lại màn hình auction-list-view.fxml
     * - Lấy cửa sổ hiện tại
     * - Thay scene hiện tại bằng scene danh sách đấu giá
     */
    @FXML
    private void handleBackToAuctionList() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        try {
            // Load file giao diện danh sách phiên đấu giá
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/auction-list-view.fxml")
            );

            Parent root = loader.load();

            // Lấy cửa sổ hiện tại thông qua một thành phần đang có trên màn hình
            Stage stage = (Stage) lblGiaHienTai.getScene().getWindow();

            // Chuyển scene hiện tại sang màn hình danh sách đấu giá
            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Danh sách phiên đấu giá");
            stage.show();

        } catch (IOException e) {
            // Nếu không load được file FXML thì hiện popup lỗi
            hienThiPopup(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không thể quay lại danh sách đấu giá!");
            e.printStackTrace();
        }
    }
    @FXML
    private void handleDongPhien() {
        try {
            /*
             * auctionName có dạng:
             * "5 - nak vt"
             *
             * Lấy auctionId là phần trước dấu " - ".
             */
            String auctionId = auctionName.split(" - ")[0].trim();

            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Xác nhận đóng phiên");
            confirmAlert.setHeaderText(null);
            confirmAlert.setContentText("Bạn có chắc muốn đóng phiên đấu giá này không?");

            if (confirmAlert.showAndWait().isEmpty()
                    || confirmAlert.getResult().getButtonData().isCancelButton()) {
                return;
            }

            String message = "CLOSE_AUCTION|" + auctionId;

            String response = networkService.sendMessage(message);

            System.out.println("Server trả về khi đóng phiên: " + response);

            if (response.startsWith("ERROR")) {
                hienThiPopup(Alert.AlertType.ERROR,
                        "Lỗi kết nối",
                        response.replace("ERROR|", ""));
                return;
            }

            if (response.startsWith("FAIL")) {
                hienThiPopup(Alert.AlertType.WARNING,
                        "Đóng phiên thất bại",
                        response.replace("FAIL|", ""));
                return;
            }

            if (response.startsWith("SUCCESS")) {
                phienDangMo = false;

                lblTrangThai.setText("Trạng thái: ĐÃ KẾT THÚC");
                lblTrangThai.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");

                txtNhapGia.setDisable(true);
                btnDatGia.setDisable(true);
                btnDongPhien.setDisable(true);

                if (countdownTimeline != null) {
                    countdownTimeline.stop();
                }

                if (refreshTimeline != null) {
                    refreshTimeline.stop();
                }

                hienThiPopup(Alert.AlertType.INFORMATION,
                        "Đóng phiên thành công",
                        response.replace("SUCCESS|", ""));

                return;
            }

            hienThiPopup(Alert.AlertType.WARNING,
                    "Phản hồi không xác định",
                    response);

        } catch (Exception e) {
            hienThiPopup(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không thể đóng phiên đấu giá!");
            e.printStackTrace();
        }
    }
    @FXML
    public void initialize() {
        // Khi đang ở ô nhập giá:
        // Bấm mũi tên xuống hoặc Enter thì chuyển focus xuống nút ĐẶT GIÁ
        txtNhapGia.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.ENTER) {
                btnDatGia.requestFocus();
                event.consume();
            }
        });

        // Khi đang ở nút ĐẶT GIÁ:
        // Bấm mũi tên xuống thì chuyển focus xuống nút QUAY LẠI
        // Bấm Enter thì đặt giá
        btnDatGia.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                if (btnDongPhien.isVisible()) {
                    btnDongPhien.requestFocus();
                } else {
                    btnQuayLai.requestFocus();
                }
                event.consume();

            } else if (event.getCode() == KeyCode.UP) {
                txtNhapGia.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.ENTER) {
                handleDatGia();
                event.consume();
            }
        });
        btnDongPhien.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                btnQuayLai.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.UP) {
                btnDatGia.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.ENTER) {
                handleDongPhien();
                event.consume();
            }
        });

        // Khi đang ở nút QUAY LẠI:
        // Bấm mũi tên lên thì quay lại nút ĐẶT GIÁ
        // Bấm Enter thì quay lại danh sách đấu giá
        btnQuayLai.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                if (btnDongPhien.isVisible()) {
                    btnDongPhien.requestFocus();
                } else {
                    btnDatGia.requestFocus();
                }
                event.consume();

            } else if (event.getCode() == KeyCode.ENTER) {
                handleBackToAuctionList();
                event.consume();
            }
        });
    }
}
