package com.auction.server.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuctionScheduler {
    // Tạo bộ máy lên lịch có thể chạy ngầm
    private ScheduledExecutorService scheduler;

    public AuctionScheduler() {
        // Cấp cho nó 1 luồng (thread) để chuyên làm nhiệm vụ đếm giờ
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public void scheduleEndAuction(int auctionId, long secondsRemaining) {
        System.out.println("Bắt đầu đếm ngược " + secondsRemaining + " giây cho phiên: " + auctionId);

        scheduler.schedule(() -> {
            // Code bên trong khối này sẽ ngủ, và tự động thức dậy chạy khi hết giờ
            System.out.println("\n[BING BOONG] Đã hết thời gian! Đang chốt đơn cho phiên: " + auctionId);

            // TODO sau này em sẽ gọi hàm: biddingService.closeAuction(auctionId)

        }
        , secondsRemaining, TimeUnit.SECONDS);
    }
}
