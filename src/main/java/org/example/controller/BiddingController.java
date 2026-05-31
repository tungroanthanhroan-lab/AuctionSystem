package org.example.controller;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.service.AppSession;
import org.example.service.AuctionDataStore;
import org.example.service.NetworkService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BiddingController {

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

    @FXML
    private Label lblTrangThai;

    @FXML
    private Button btnDongPhien;

    // ── Bid-History Chart ──────────────────────────────────────────────────
    @FXML
    private LineChart<Number, Number> bidChart;

    @FXML
    private NumberAxis xAxis;

    @FXML
    private NumberAxis yAxis;

    /** Single data series shown on the chart. */
    private XYChart.Series<Number, Number> bidSeries;

    /**
     * Epoch-second of the first bid recorded for this session.
     * All subsequent X-axis values are expressed as "seconds since first bid"
     * so the axis stays readable.
     */
    private long chartEpochOrigin = -1;
    // ──────────────────────────────────────────────────────────────────────

    private Timeline countdownTimeline;
    private Timeline refreshTimeline;

    private LocalDateTime auctionEndTime;

    private String auctionName;
    private String currentAuctionId;

    private boolean phienDangMo = true;

    private final NetworkService networkService = new NetworkService();

    private double giaHienTai = 0.0;

    @FXML
    private Button btnAutoBid;


    // ── Các định dạng datetime mà server có thể trả về ────────────────────
    private static final DateTimeFormatter FMT_SPACE  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FMT_T_SEC  = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter FMT_SPACE_MIN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    // ISO_LOCAL_DATE_TIME xử lý "yyyy-MM-ddTHH:mm" và "yyyy-MM-ddTHH:mm:ss"
    // ──────────────────────────────────────────────────────────────────────

    // ── Chart initialisation ───────────────────────────────────────────────
    /**
     * Called automatically by the FXML loader after all @FXML fields are
     * injected.  We set up the series here so the chart is ready before
     * setAuctionInfo() fires.
     */
    @FXML
    public void initialize() {
        // Keyboard shortcuts (unchanged from original)
        txtNhapGia.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.ENTER) {
                btnDatGia.requestFocus();
                event.consume();
            }
        });

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

        btnAutoBid.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleOpenAutoBid();
                event.consume();
            }
        });


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

        // ── Chart setup ────────────────────────────────────────────────────
        bidSeries = new XYChart.Series<>();
        bidSeries.setName("Giá đặt ($)");

        if (bidChart != null) {
            bidChart.getData().add(bidSeries);
            bidChart.setAnimated(false);          // smoother live updates
            bidChart.setCreateSymbols(true);
            bidChart.setLegendVisible(false);

            if (xAxis != null) {
                xAxis.setAutoRanging(false);

                xAxis.setLowerBound(0);
                xAxis.setUpperBound(10);

                xAxis.setTickUnit(1);

                xAxis.setMinorTickVisible(false);

                xAxis.setTickLabelFormatter(
                        new javafx.util.StringConverter<>() {

                            @Override
                            public String toString(Number value) {
                                return String.format("%.0fs", value.doubleValue());
                            }

                            @Override
                            public Number fromString(String string) {
                                return 0;
                            }
                        }
                );
            }

            if (yAxis != null) {
                yAxis.setAutoRanging(true);
                yAxis.setForceZeroInRange(false);
            }
        }
    }
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Thử parse chuỗi datetime với nhiều định dạng khác nhau.
     * Server có thể trả về các format: "yyyy-MM-ddTHH:mm", "yyyy-MM-ddTHH:mm:ss",
     * "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss".
     *
     * @return LocalDateTime nếu parse thành công, null nếu thất bại.
     */

    private LocalDateTime parseEndTime(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return null;

        String s = raw.trim();

        // Thử từng định dạng theo thứ tự phổ biến nhất
        // 1. "yyyy-MM-dd HH:mm:ss" (SQLite thường lưu dạng này)
        try { return LocalDateTime.parse(s, FMT_SPACE); } catch (Exception ignored) {}

        // 2. "yyyy-MM-ddTHH:mm:ss"
        try { return LocalDateTime.parse(s, FMT_T_SEC); } catch (Exception ignored) {}

        // 3. "yyyy-MM-dd HH:mm" (không có giây)
        try { return LocalDateTime.parse(s, FMT_SPACE_MIN); } catch (Exception ignored) {}

        // 4. ISO standard "yyyy-MM-ddTHH:mm" hoặc "yyyy-MM-ddTHH:mm:ss" (Java built-in)
        try { return LocalDateTime.parse(s); } catch (Exception ignored) {}

        // 5. Thử thay dấu cách thành T rồi parse lại (phòng ngừa format hỗn hợp)
        try { return LocalDateTime.parse(s.replace(" ", "T")); } catch (Exception ignored) {}

        return null; // Tất cả đều thất bại
    }

    @FXML
    public void setAuctionInfo(String auctionInfo) {
        try {
            String originalAuctionInfo = auctionInfo;

            String[] mainParts = auctionInfo.split(" - Giá hiện tại:");
            auctionName = mainParts[0].trim();

            String[] auctionNameParts = auctionName.split(" - ", 2);
            currentAuctionId = auctionNameParts[0].trim();

            String displayTitle = auctionName;

            String[] nameParts = auctionName.split(" - ", 2);
            if (nameParts.length == 2) {
                displayTitle = nameParts[1].trim();
            }

            lblTenSanPham.setText(displayTitle);

            giaHienTai = 0.0;

            if (mainParts.length > 1) {
                String priceAndStatusPart = mainParts[1];
                String priceText = priceAndStatusPart.split("\\$")[0].trim();
                giaHienTai = Double.parseDouble(priceText);
            }

            lblGiaHienTai.setText("Giá hiện tại: " + giaHienTai + " $");

            String highestBidder = "Chưa có";

            if (originalAuctionInfo.contains("Người dẫn đầu:")) {
                highestBidder = originalAuctionInfo
                        .substring(originalAuctionInfo.indexOf("Người dẫn đầu:") + "Người dẫn đầu:".length())
                        .trim();

                if (highestBidder.contains(" - Kết thúc:")) {
                    highestBidder = highestBidder.substring(0, highestBidder.indexOf(" - Kết thúc:")).trim();
                }

                if (highestBidder.contains(" - Trạng thái:")) {
                    highestBidder = highestBidder.substring(0, highestBidder.indexOf(" - Trạng thái:")).trim();
                }

                if (highestBidder.isEmpty() || highestBidder.equals("-")) {
                    highestBidder = "Chưa có";
                }
            } else {
                String localHighestBidder = AuctionDataStore.getHighestBidder(auctionName);

                if (localHighestBidder != null && !localHighestBidder.trim().isEmpty()) {
                    highestBidder = localHighestBidder;
                }
            }

            lblNguoiDanDau.setText("Người dẫn đầu: " + highestBidder);

            loadBidHistoryFromServer();

            String status = null;
            String endTimeText = null;

            if (originalAuctionInfo.contains("Kết thúc:")) {
                endTimeText = originalAuctionInfo
                        .substring(originalAuctionInfo.indexOf("Kết thúc:") + "Kết thúc:".length())
                        .trim();
            }

            if (originalAuctionInfo.contains("Trạng thái:")) {
                status = originalAuctionInfo
                        .substring(originalAuctionInfo.indexOf("Trạng thái:") + "Trạng thái:".length())
                        .trim();

                if (status.contains(" - Người dẫn đầu:")) {
                    status = status.substring(0, status.indexOf(" - Người dẫn đầu:")).trim();
                }

                if (status.contains(" - Kết thúc:")) {
                    status = status.substring(0, status.indexOf(" - Kết thúc:")).trim();
                }
            }

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
                phienDangMo = AuctionDataStore.isAuctionOpen(auctionName);

                if (phienDangMo) {
                    lblTrangThai.setText("Trạng thái: ĐANG MỞ");
                    lblTrangThai.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                } else {
                    lblTrangThai.setText("Trạng thái: ĐÃ KẾT THÚC");
                    lblTrangThai.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                }
            }

            if (endTimeText != null && !endTimeText.isEmpty() && !endTimeText.equals("-")) {
                auctionEndTime = parseEndTime(endTimeText);
                if (auctionEndTime != null) {
                    startCountdown();
                } else {
                    lblCountdown.setText("Thời gian còn lại: Không xác định");
                    System.out.println("[Countdown] Không parse được endTime: '" + endTimeText + "'");
                }
            } else {
                lblCountdown.setText("Thời gian còn lại: Không xác định");
                System.out.println("[Countdown] auctionInfo không có endTime: " + originalAuctionInfo);
            }
            // ──────────────────────────────────────────────────────────────

            txtNhapGia.setDisable(!phienDangMo);
            btnDatGia.setDisable(!phienDangMo);

            btnDongPhien.setVisible(true);
            btnDongPhien.setManaged(true);
            btnDongPhien.setDisable(!phienDangMo);

            startAutoRefresh();

        } catch (Exception e) {
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

        if (!phienDangMo) {
            hienThiPopup(Alert.AlertType.ERROR, "Phiên đã đóng", "Phiên đấu giá đã kết thúc!");
            return;
        }

        if (inputStr == null || inputStr.trim().isEmpty()) {
            hienThiPopup(Alert.AlertType.WARNING, "Cảnh báo", "Bạn chưa đặt giá tiền!");
            return;
        }

        try {
            double giaDat = Double.parseDouble(inputStr.trim());

            if (giaDat <= giaHienTai) {
                hienThiPopup(Alert.AlertType.ERROR, "Lỗi đặt giá", "Phải đặt giá cao hơn " + giaHienTai + "$");
                return;
            }

            // 1. Khóa nút ngay lập tức để tránh bấm loạn xạ
            btnDatGia.setDisable(true);

            // 2. Chạy việc gửi tin nhắn trong một Thread riêng để không làm đơ giao diện
            new Thread(() -> {
                try {
                    String auctionId = currentAuctionId;
                    String message = "BID|" + auctionId + "|" + giaDat;

                    String response = networkService.sendMessage(message);
                    System.out.println("Server trả về: " + response);

                    // 3. Quay lại luồng UI để cập nhật giao diện sau khi có phản hồi
                    Platform.runLater(() -> {
                        btnDatGia.setDisable(false);

                        if (response == null || response.trim().isEmpty()) {
                            hienThiPopup(Alert.AlertType.ERROR, "Lỗi kết nối", "Server không trả về phản hồi!");
                            return;
                        }

                        if (response.startsWith("ERROR")) {
                            hienThiPopup(Alert.AlertType.ERROR, "Lỗi kết nối", response.replace("ERROR|", ""));
                            return;
                        }

                        if (response.startsWith("FAIL")) {
                            refreshCurrentAuctionFromServer();
                            hienThiPopup(Alert.AlertType.WARNING, "Đặt giá thất bại", response.replace("FAIL|", ""));
                            return;
                        }

                        if (response.startsWith("SUCCESS")) {
                            giaHienTai = giaDat;
                            String username = AppSession.getCurrentUsername();

                            lblGiaHienTai.setText("Giá hiện tại: " + giaHienTai + " $");
                            lblNguoiDanDau.setText("Người dẫn đầu: " + username);

                            loadBidHistoryFromServer();
                            txtNhapGia.clear();

                            hienThiPopup(Alert.AlertType.INFORMATION, "Đặt giá thành công", "Bạn đã đặt giá " + giaDat + " $ thành công!");
                        }
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        btnDatGia.setDisable(false);
                        System.out.println("Lỗi khi đặt giá: " + e.getMessage());
                    });
                }
            }).start();

        } catch (NumberFormatException e) {
            hienThiPopup(Alert.AlertType.ERROR, "Lỗi nhập liệu", "Vui lòng chỉ nhập số!");
        }
    }

    @FXML
    private void handleOpenAutoBid() {
        if (!phienDangMo) {
            hienThiPopup(Alert.AlertType.WARNING,
                    "Phiên đã đóng",
                    "Phiên đấu giá đã kết thúc, không thể đặt auto-bid!");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/auto-bid-view.fxml")
            );
            Parent root = loader.load();

            AutoBidController ctrl = loader.getController();
            ctrl.init(currentAuctionId, giaHienTai, networkService);

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(btnAutoBid.getScene().getWindow());
            dialog.setTitle("Đặt giá tự động - Phiên " + currentAuctionId);
            dialog.setResizable(false);
            dialog.setScene(new Scene(root));
            dialog.showAndWait();
        } catch (IOException e) {
            hienThiPopup(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không mở được cửa sổ Auto-Bid: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Được gọi từ bên ngoài (ví dụ AutoBidController hoặc real-time push)
     * để cập nhật giá mới lên UI mà không cần refresh toàn bộ.
     */
    public void nhanGiaMoiTuServer(double giaMoi) {
        this.giaHienTai = giaMoi;
        capNhatGiaTrenUI(giaMoi, null);
    }

    private void capNhatGiaTrenUI(double giaMoi, String currentLeader) {
        Platform.runLater(() -> {
            lblGiaHienTai.setText("Giá hiện tại: " + giaMoi + " $");

            if (currentLeader != null
                    && !currentLeader.trim().isEmpty()
                    && !currentLeader.equals("-")) {
                lblNguoiDanDau.setText("Người dẫn đầu: " + currentLeader);
            } else {
                lblNguoiDanDau.setText("Người dẫn đầu: Chưa có");
            }
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

        long totalMinutes = seconds / 60;
        long hours   = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        long days    = hours / 24;
        long remHours = hours % 24;

        String timeText;
        if (days > 0) {
            timeText = String.format("%d ngày %d giờ %02d phút", days, remHours, minutes);
        } else if (hours > 0) {
            timeText = String.format("%d giờ %02d phút", hours, minutes);
        } else {
            long secs = seconds % 60;
            timeText = String.format("%d phút %02d giây", minutes, secs);
        }
        lblCountdown.setText("Thời gian còn lại: " + timeText);
    }

    private void startAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }

        refreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(5), event -> {
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

            if (response == null || response.startsWith("ERROR")) {
                throw new Exception(response);
            }

            if (!response.startsWith("AUCTIONS")) {
                return;
            }

            String[] parts = response.split("\\|");
            for (int i = 1; i < parts.length; i++) {
                String auctionData = parts[i];
                String[] fields = auctionData.split(",", -1);

                if (fields.length < 4) continue;

                String auctionId = fields[0].trim();
                if (!auctionId.equals(currentAuctionId)) continue;

                double serverPrice = Double.parseDouble(fields[2].trim());
                String status = fields[3].trim();
                String currentLeader = (fields.length >= 5) ? fields[4].trim() : "";
                String refreshedEndTime = (fields.length >= 6) ? fields[5].trim() : (fields.length == 5 ? fields[4].trim() : "");

                Platform.runLater(() -> {
                    lblTrangThai.setOpacity(1.0);
                    listLichSuBid.setOpacity(1.0);

                    if (serverPrice != giaHienTai) {
                        giaHienTai = serverPrice;
                        lblGiaHienTai.setText("Giá hiện tại: " + giaHienTai + " $");

                        if (!currentLeader.isEmpty() && !currentLeader.equals("-")) {
                            lblNguoiDanDau.setText("Người dẫn đầu: " + currentLeader);
                        } else {
                            lblNguoiDanDau.setText("Người dẫn đầu: Chưa có");
                        }

                        loadBidHistoryFromServer();
                    }

                    if (!refreshedEndTime.isEmpty() && !refreshedEndTime.equals("-")) {
                        auctionEndTime = parseEndTime(refreshedEndTime);
                    }

                    boolean serverAuctionOpen = status.equalsIgnoreCase("OPEN") || status.equalsIgnoreCase("RUNNING");
                    if (serverAuctionOpen) {
                        lblTrangThai.setText("Trạng thái: ĐANG MỞ");
                        lblTrangThai.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else if (phienDangMo) {
                        phienDangMo = false;
                        lblTrangThai.setText("Trạng thái: ĐÃ KẾT THÚC");
                        lblTrangThai.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                        txtNhapGia.setDisable(true);
                        btnDatGia.setDisable(true);
                    }
                });
                return;
            }

        } catch (Exception e) {
            System.out.println("[AutoRefresh] Tạm thời mất kết nối: " + e.getMessage());

            Platform.runLater(() -> {
                lblTrangThai.setText("Trạng thái: Đang kết nối lại...");
                lblTrangThai.setStyle("-fx-text-fill: gray;");

                listLichSuBid.setOpacity(0.5);
            });
        }
    }

    private void hienThiPopup(Alert.AlertType loaiPopup, String tieuDe, String noiDung) {
        Alert alert = new Alert(loaiPopup);
        alert.setTitle(tieuDe);
        alert.setHeaderText(null);
        alert.setContentText(noiDung);
        alert.showAndWait();
    }

    @FXML
    private void handleBackToAuctionList() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/auction-list-view.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) lblGiaHienTai.getScene().getWindow();

            double currentWidth = stage.getWidth();
            double currentHeight = stage.getHeight();

            stage.setScene(new Scene(root, currentWidth, currentHeight));
            stage.setTitle("Danh sách phiên đấu giá");
            stage.show();

        } catch (IOException e) {
            hienThiPopup(Alert.AlertType.ERROR,
                    "Lỗi",
                    "Không thể quay lại danh sách đấu giá!");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDongPhien() {
        try {
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

            if (response == null || response.trim().isEmpty()) {
                hienThiPopup(Alert.AlertType.ERROR,
                        "Lỗi",
                        "Server không trả về phản hồi!");
                return;
            }

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

    // ── Bid history + chart ────────────────────────────────────────────────

    private void loadBidHistoryFromServer() {
        new Thread(() -> {
            try {
                if (currentAuctionId == null || currentAuctionId.trim().isEmpty()) return;

                String response = networkService.sendMessage("GET_BID_HISTORY|" + currentAuctionId);
                System.out.println("Server trả về lịch sử bid: " + response);

                if (response == null || !response.startsWith("BID_HISTORY")) return;

                // Lưu vào biến final để dùng được trong lambda
                final String finalResponse = response;
                String[] parts = response.split("\\|");

                Platform.runLater(() -> {
                    listLichSuBid.getItems().clear();

                    if (parts.length <= 2) {
                        listLichSuBid.getItems().add("Chưa có lượt đặt giá nào.");
                        rebuildChart(finalResponse); // vẫn gọi để clear chart
                        return;
                    }

                    for (int i = 2; i < parts.length; i++) {
                        String[] fields = parts[i].split(",", -1);
                        if (fields.length >= 4) {
                            String username = fields[1].trim();
                            String amount   = fields[2].trim();
                            String time     = fields[3].trim();
                            listLichSuBid.getItems().add(
                                    username + " đã đặt " + amount + " $ lúc " + time
                            );
                        } else {
                            listLichSuBid.getItems().add(parts[i]);
                        }
                    }

                    rebuildChart(finalResponse); // ← BỎ COMMENT + dùng finalResponse
                });

            } catch (Exception e) {
                System.out.println("[BidHistory] Lỗi load lịch sử: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Rebuilds the LineChart from the raw BID_HISTORY response string.
     *
     * Each data point is (elapsedSeconds, bidAmount).  Using elapsed seconds
     * keeps the X-axis compact regardless of when the auction started.
     *
     * Format expected: BID_HISTORY|auctionId|userId,username,bidAmount,bidTime|...
     * bidTime format: "yyyy-MM-dd HH:mm:ss" or "yyyy-MM-ddTHH:mm:ss"
     */
    private void rebuildChart(String bidHistoryResponse) {
        if (bidChart == null || bidSeries == null) return;

        Platform.runLater(() -> {
            bidSeries.getData().clear();
            chartEpochOrigin = -1;

            String[] parts = bidHistoryResponse.split("\\|");
            if (parts.length <= 2) return;

            DateTimeFormatter fmt1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            DateTimeFormatter fmt2 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

            java.util.List<double[]> bidPairs = new java.util.ArrayList<>();

            for (int i = 2; i < parts.length; i++) {
                String[] fields = parts[i].split(",", -1);
                if (fields.length < 4) continue;

                try {
                    double amount = Double.parseDouble(fields[2].trim());
                    String timeStr = fields[3].trim();

                    LocalDateTime bidTime;
                    try {
                        bidTime = LocalDateTime.parse(timeStr, fmt1);
                    } catch (Exception ex) {
                        bidTime = LocalDateTime.parse(timeStr, fmt2);
                    }

                    long epochSec = bidTime.atZone(java.time.ZoneId.systemDefault())
                            .toEpochSecond();

                    bidPairs.add(new double[]{epochSec, amount});

                } catch (Exception ex) {
                    System.out.println("[Chart] Không parse được bid entry: " + parts[i]);
                }
            }

            if (bidPairs.isEmpty()) return;

            bidPairs.sort((a, b) -> Double.compare(a[0], b[0]));

            chartEpochOrigin = (long) bidPairs.get(0)[0];

            for (double[] pair : bidPairs) {
                long elapsed = (long) pair[0] - chartEpochOrigin;
                double amount = pair[1];

                bidSeries.getData().add(
                        new XYChart.Data<>(elapsed, amount)
                );
            }

            if (!bidPairs.isEmpty()) {
                long maxElapsed =
                        (long) bidPairs.get(bidPairs.size() - 1)[0]
                                - chartEpochOrigin;

                xAxis.setUpperBound(
                        Math.max(10, maxElapsed + 2)
                );
            }

        });
    }
}