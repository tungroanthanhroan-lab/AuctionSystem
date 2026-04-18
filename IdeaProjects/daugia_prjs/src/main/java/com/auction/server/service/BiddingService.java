package com.auction.server.service;

import java.util.concurrent.locks.ReentrantLock;

public class BiddingService {
    // bao ve he thong tranh nhieu nguoi bid cung 1ms
    private final ReentrantLock lock = new ReentrantLock();

    // gia lap gia ban san pham, sau se lay tu Database
    private double currentHighestPrice = 10000.0;
    private int currentWinnerId = -1;

    // ham dat gia
    public boolean placeBid(int userId, double bidAmount) throws Exception {
        lock.lock(); // den sau = doi
        try {
            System.out.println("User " + userId + " đang xử lý đặt giá: " + bidAmount);

            // kiem tra tinh hop le
            if (bidAmount <= currentHighestPrice) {
                System.out.println("-> Thất bại: Giá quá thấp!");
                throw new Exception("Giá đặt phải cao hơn giá hiện hành!");
            }

            // 3. hop le -> cap nhat gia moi
            currentHighestPrice = bidAmount;
            currentWinnerId = userId;
            System.out.println("-> Thành công! Người dẫn đầu mới là User " + userId + " với giá " + bidAmount);

            return true;
        } finally {
            lock.unlock(); // tiep tuc de nguoi khac bid
        }
    }
}
