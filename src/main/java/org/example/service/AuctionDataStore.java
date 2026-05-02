package org.example.service;

import java.util.LinkedHashMap;
import java.util.Map;

public class AuctionDataStore {

    /*
     * Lưu tạm giá hiện tại của các phiên đấu giá.
     * Dùng LinkedHashMap để giữ đúng thứ tự hiển thị trong danh sách.
     *
     * Đây chỉ là dữ liệu tạm trên client để demo UI.
     * Sau này bản chuẩn sẽ lấy dữ liệu từ server/database.
     */
    private static final Map<String, Double> currentPrices = new LinkedHashMap<>();

    static {
        currentPrices.put("1 - Thanh xuân của bạn", 0.0);
        currentPrices.put("2 - Laptop Gaming", 1000.0);
        currentPrices.put("3 - Tranh nghệ thuật", 500.0);
    }

    /*
     * Trả về toàn bộ danh sách phiên đấu giá và giá hiện tại.
     */
    public static Map<String, Double> getCurrentPrices() {
        return currentPrices;
    }

    /*
     * Lấy giá hiện tại của một phiên đấu giá.
     */
    public static double getPrice(String auctionName) {
        return currentPrices.getOrDefault(auctionName, 0.0);
    }

    /*
     * Cập nhật giá mới sau khi người dùng đặt giá thành công.
     */
    public static void updatePrice(String auctionName, double newPrice) {
        currentPrices.put(auctionName, newPrice);
    }
}