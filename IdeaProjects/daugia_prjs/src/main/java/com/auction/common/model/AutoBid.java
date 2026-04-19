package com.auction.common.model;

import java.time.Instant;

public class AutoBid {
    private int userId;
    private double maxBid;
    private double increment;
    private Instant registeredAt; // Dùng để ưu tiên ai đăng ký trước

    public AutoBid(int userId, double maxBid, double increment) {
        this.userId = userId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.registeredAt = Instant.now(); 
    }

    // Getters
    public int getUserId() { return userId; }
    public double getMaxBid() { return maxBid; }
    public double getIncrement() { return increment; }
    public Instant getRegisteredAt() { return registeredAt; }
}
