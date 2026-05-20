package org.example.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuctionDataStore {

    /*
     * Lưu giá hiện tại của từng phiên đấu giá.
     * Ví dụ: "2 - Laptop Gaming" -> 1500.0
     */
    private static final Map<String, Double> currentPrices = new LinkedHashMap<>();

    /*
     * Lưu người đang dẫn đầu của từng phiên đấu giá.
     * Ví dụ: "2 - Laptop Gaming" -> "phuc"
     */
    private static final Map<String, String> highestBidders = new LinkedHashMap<>();

    /*
     * Lưu lịch sử đặt giá của từng phiên đấu giá.
     * Ví dụ: "2 - Laptop Gaming" -> ["phuc đã đặt 1500.0$"]
     */
    private static final Map<String, List<String>> bidHistories = new LinkedHashMap<>();

    /*
     * Lưu trạng thái phiên.
     * true  = đang mở
     * false = đã kết thúc
     */
    private static final Map<String, Boolean> auctionOpenStatus = new LinkedHashMap<>();

    /*
     * Dữ liệu demo ban đầu.
     */
    static {
        addAuction("1 - Thanh xuân của bạn", 0.0);
        addAuction("2 - Laptop Gaming", 1000.0);
        addAuction("3 - Tranh nghệ thuật", 500.0);
    }

    /*
     * Thêm một phiên đấu giá vào kho dữ liệu tạm.
     */
    private static void addAuction(String auctionName, double startPrice) {
        currentPrices.put(auctionName, startPrice);
        highestBidders.put(auctionName, "Chưa có");
        bidHistories.put(auctionName, new ArrayList<>());
        auctionOpenStatus.put(auctionName, true);
    }
    /*
     * Hàm public để Seller tạo phiên đấu giá mới từ giao diện.
     * CreateAuctionController sẽ gọi hàm này.
     */
    public static void addNewAuction(String auctionName, double startPrice) {
        addAuction(auctionName, startPrice);
    }

    /*
     * Trả về danh sách phiên và giá hiện tại.
     * AuctionListController dùng để hiển thị list.
     */
    public static Map<String, Double> getCurrentPrices() {
        return currentPrices;
    }

    /*
     * Lấy giá hiện tại của một phiên.
     */
    public static double getPrice(String auctionName) {
        return currentPrices.getOrDefault(auctionName, 0.0);
    }

    /*
     * Lấy người đang dẫn đầu.
     */
    public static String getHighestBidder(String auctionName) {
        return highestBidders.getOrDefault(auctionName, "Chưa có");
    }

    /*
     * Lấy lịch sử đặt giá.
     */
    public static List<String> getBidHistory(String auctionName) {
        return bidHistories.getOrDefault(auctionName, new ArrayList<>());
    }

    /*
     * Kiểm tra phiên còn mở không.
     */
    public static boolean isAuctionOpen(String auctionName) {
        return auctionOpenStatus.getOrDefault(auctionName, true);
    }

    /*
     * Cập nhật dữ liệu sau khi đặt giá hợp lệ.
     */
    public static void updateBid(String auctionName, double newPrice, String bidderName) {
        currentPrices.put(auctionName, newPrice);
        highestBidders.put(auctionName, bidderName);

        bidHistories
                .computeIfAbsent(auctionName, key -> new ArrayList<>())
                .add(bidderName + " đã đặt " + newPrice + "$");
    }

    /*
     * Đóng phiên đấu giá.
     */
    public static void closeAuction(String auctionName) {
        auctionOpenStatus.put(auctionName, false);
    }
}