package org.example.dao;

import org.example.model.Item;
import org.example.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    // Hàm tạo bảng items (Có liên kết Khóa ngoại với bảng users)
    public void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS items ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL,"
                + "description TEXT,"
                + "starting_price REAL NOT NULL,"
                + "current_price REAL NOT NULL,"
                + "end_time TEXT NOT NULL,"
                + "seller_id INTEGER NOT NULL,"
                + "status TEXT NOT NULL,"
                + "FOREIGN KEY(seller_id) REFERENCES users(id)"
                + ");";

        Connection conn = DatabaseConnection.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.execute();
            System.out.println("Đã kiểm tra/tạo bảng items.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Hàm đăng bán món hàng mới
    public boolean createItem(String title, String description) {
        String sql = "INSERT INTO items(title, description) VALUES(?,?)";
        Connection conn = DatabaseConnection.getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, title);
            pstmt.setString(2, description);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Hàm lấy danh sách tất cả món hàng để hiển thị lên giao diện
    public List<Item> getAllItems() {
        List<Item> itemList = new ArrayList<>();
        String sql = "SELECT * FROM items";
        Connection conn = DatabaseConnection.getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Item item = new Item(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("description")
                );
                itemList.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return itemList;
    }

    // Cập nhật giá mới khi có người đấu giá thành công
    public boolean updateCurrentPrice(int itemId, double newPrice) {
        String sql = "UPDATE items SET current_price = ? WHERE id = ?";
        Connection conn = DatabaseConnection.getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, newPrice);
            pstmt.setInt(2, itemId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}