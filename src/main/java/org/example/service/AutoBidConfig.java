package org.example.service;

import org.example.model.Bidder;

import java.time.LocalDateTime;

/**
 * Lưu cấu hình auto-bid mà một Bidder đăng ký cho một phiên đấu giá.
 * Bidder đặt trước: giá tối đa (maxBid) và bước giá (increment).
 * Hệ thống sẽ tự động trả giá thay họ khi có đối thủ vượt qua.
 */
public class AutoBidConfig {
    private final Bidder bidder;
    private final double maxBid;       // Giới hạn tối đa bidder chấp nhận trả
    private final double increment;    // Mỗi lần auto-bid tăng thêm bao nhiêu
    private final LocalDateTime registeredAt; // Dùng để ưu tiên khi 2 auto-bid bằng nhau

    public AutoBidConfig(Bidder bidder, double maxBid, double increment) {
        if (maxBid <= 0) throw new IllegalArgumentException("maxBid phải lớn hơn 0");
        if (increment <= 0) throw new IllegalArgumentException("increment phải lớn hơn 0");

        this.bidder = bidder;
        this.maxBid = maxBid;
        this.increment = increment;
        this.registeredAt = LocalDateTime.now();
    }

    public Bidder getBidder() {
        return bidder;
    }

    public double getMaxBid() {
        return maxBid;
    }

    public double getIncrement() {
        return increment;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    @Override
    public String toString() {
        return "AutoBidConfig{bidder=" + bidder.getUsername()
                + ", maxBid=" + maxBid
                + ", increment=" + increment
                + ", registeredAt=" + registeredAt + "}";
    }
}
