package com.auction.common.model;

import java.time.LocalDateTime;

public class AutoBid {
    private int userId;
    private double maxBid;
    private double increment;
    private LocalDateTime registrationTime; // Dùng để ưu tiên ai đăng ký trước

    public AutoBid(int userId, double maxBid, double increment, LocalDateTime registrationTime) {
        this.userId = userId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.registrationTime = registrationTime;
    }

    public int getUserId() { return userId; }
    public double getMaxBid() { return maxBid; }
    public double getIncrement() { return increment; }
    public LocalDateTime getRegistrationTime() { return registrationTime; }
}
