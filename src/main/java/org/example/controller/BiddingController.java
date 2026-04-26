package org.example.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.application.Platform;
public class BiddingController {
    //khai bao cac thanh phan co fx:id ben file FXML
    @FXML
    private Label lblGiaHienTai;
    @FXML
    private TextField txtNhapGia;
    //tam
    private double giaHienTai = 0.0;

    @FXML
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
                // Đặt giá hợp lệ -> Tạm thời cho cập nhật lên màn hình
                giaHienTai = giaDat;
                lblGiaHienTai.setText("Giá hiện tại: "+giaHienTai+"$");
                txtNhapGia.clear();//xóa trắng ô nhập
                hienThiPopup(Alert.AlertType.INFORMATION, "Thành công", "Đã gửi yêu cầu đặt giá");
                //(Sau này chỗ này sẽ gửi mức giá này lên cho sever)
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
}
