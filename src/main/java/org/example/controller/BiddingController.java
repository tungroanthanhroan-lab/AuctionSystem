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

    // Tên phiên đấu giá hiện tại đang được mở
    @FXML
    private String auctionName;

    // Dùng để gửi dữ liệu từ màn hình đấu giá lên server
    private NetworkService networkService = new NetworkService();
    //tam
    private double giaHienTai = 0.0;

    @FXML
    public void setAuctionInfo(String auctionInfo) {
        try {
            /*
             * auctionInfo có dạng:
             * "2 - Laptop Gaming - Giá hiện tại: 1000.0$"
             *
             * Ta tách ra để lấy phần tên:
             * "2 - Laptop Gaming"
             */
            String[] mainParts = auctionInfo.split(" - Giá hiện tại:");
            auctionName = mainParts[0];

            // Hiển thị tên sản phẩm/phiên đấu giá
            lblTenSanPham.setText("Sản phẩm: " + auctionName);

            // Lấy giá hiện tại từ AuctionDataStore thay vì lấy từ chuỗi
            giaHienTai = AuctionDataStore.getPrice(auctionName);
            lblGiaHienTai.setText("Giá hiện tại: " + giaHienTai + " $");

        } catch (Exception e) {
            // Nếu có lỗi khi tách chuỗi thì vẫn hiển thị được thông tin cơ bản
            auctionName = auctionInfo;
            lblTenSanPham.setText("Sản phẩm: " + auctionInfo);
            giaHienTai = 0.0;
            lblGiaHienTai.setText("Giá hiện tại: 0 $");
        }
    }
    public void handleDatGia() {
        String inputStr = txtNhapGia.getText();
        //kiem tra neu ko nhap j or nhap toan dau cach ma van bam nut dat gia
        if (inputStr == null || inputStr.trim().isEmpty()) {
            hienThiPopup(Alert.AlertType.WARNING, "Cảnh báo", "Bạn chưa đặt giá tiền!");
            return;
        }
        try {
            double giaDat = Double.parseDouble(inputStr);
            //bat loi dat gia thap hon gia hien tai
            if (giaDat <= giaHienTai){
                hienThiPopup(Alert.AlertType.ERROR, "Lỗi đặt giá", "Phải đặt giá cao hơn "+giaHienTai+"$");
            } else {
                // Tạo message gửi lên server
                // Tạm thời hard-code username là admin
                String username = "admin";
                String message = "BID|" + username + "|" + giaDat;

                // Gửi giá đặt lên server thông qua NetworkService
                String response = networkService.sendMessage(message);

                System.out.println("Server trả về: " + response);

                // Nếu không kết nối được server thì báo lỗi
                if (response.startsWith("ERROR")) {
                    hienThiPopup(Alert.AlertType.ERROR, "Lỗi kết nối", "Không kết nối được tới server!");
                    return;
                }

                giaHienTai = giaDat;

                // Cập nhật giá mới vào kho dữ liệu tạm
                // để khi quay lại AuctionList thì list vẫn hiện giá mới
                AuctionDataStore.updatePrice(auctionName, giaDat);

                lblGiaHienTai.setText("Giá hiện tại: " + giaHienTai + "$");

                lblNguoiDanDau.setText("Người dẫn đầu: admin");
                listLichSuBid.getItems().add("admin đã đặt " + giaDat + "$");

                txtNhapGia.clear();

                hienThiPopup(Alert.AlertType.INFORMATION, "Server phản hồi", response);
            }
        } catch (NumberFormatException e){
            //lỗi nhập liệu của người dùng(một ngàn thay vì 1000
            hienThiPopup(Alert.AlertType.ERROR, "lỗi nhập liệu", "Vui lòng chỉ nhập số (ví dụ:1000), không nhập chữ!");
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
        try {
            // Load file giao diện danh sách phiên đấu giá
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/auction-list-view.fxml")
            );

            Parent root = loader.load();

            // Lấy cửa sổ hiện tại thông qua một thành phần đang có trên màn hình
            Stage stage = (Stage) lblGiaHienTai.getScene().getWindow();

            // Chuyển scene hiện tại sang màn hình danh sách đấu giá
            stage.setScene(new Scene(root));
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
                btnQuayLai.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.UP) {
                txtNhapGia.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.ENTER) {
                handleDatGia();
                event.consume();
            }
        });

        // Khi đang ở nút QUAY LẠI:
        // Bấm mũi tên lên thì quay lại nút ĐẶT GIÁ
        // Bấm Enter thì quay lại danh sách đấu giá
        btnQuayLai.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.UP) {
                btnDatGia.requestFocus();
                event.consume();

            } else if (event.getCode() == KeyCode.ENTER) {
                handleBackToAuctionList();
                event.consume();
            }
        });
    }
}
