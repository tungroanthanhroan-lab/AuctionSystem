package org.example.observer;

import org.example.model.Bidder; // Import đúng đường dẫn trong project của bạn

import java.io.Serializable;

public class BidUpdateEvent implements Serializable {
    // Đổi thuộc tính Auction thành String auctionId để dễ dàng truyền qua Socket
    private String auctionId;
    private double newHighestAmount;
    private Bidder bidder;
    private String timestamp;

    public BidUpdateEvent(String auctionId, double newHighestAmount, Bidder bidder, String timestamp) {
        this.auctionId = auctionId;
        this.newHighestAmount = newHighestAmount;
        this.bidder = bidder;
        this.timestamp = timestamp;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public double getNewHighestAmount() {
        return newHighestAmount;
    }

    public void setNewHighestAmount(double newHighestAmount) {
        this.newHighestAmount = newHighestAmount;
    }

    public Bidder getBidder() {
        return bidder;
    }

    public void setBidder(Bidder bidder) {
        this.bidder = bidder;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}