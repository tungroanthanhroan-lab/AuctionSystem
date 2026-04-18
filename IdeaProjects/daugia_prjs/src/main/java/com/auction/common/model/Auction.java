package com.auction.common.model;

import java.time.LocalDateTime;

public class Auction {
    private int auctionId;
    private Item item;         // 1 mon hang
    private Seller seller;     // nguoi ban
    private String status;     // trang thai: "OPEN", "RUNNING", "FINISHED"
    private double currentHighestBid;
    private Bidder currentWinner;
    private LocalDateTime endTime; // thoi gian cho gia

    public Auction(int auctionId, Item item, Seller seller, double startPrice, LocalDateTime endTime) {
        this.auctionId = auctionId;
        this.item = item;
        this.seller = seller;
        this.currentHighestBid = startPrice;
        this.status = "OPEN";
        this.endTime = endTime;
    }

    public int getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(int auctionId) {
        this.auctionId = auctionId;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Seller getSeller() {
        return seller;
    }

    public void setSeller(Seller seller) {
        this.seller = seller;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getCurrentHighestBid() {
        return currentHighestBid;
    }

    public void setCurrentHighestBid(double currentHighestBid) {
        this.currentHighestBid = currentHighestBid;
    }

    public Bidder getCurrentWinner() {
        return currentWinner;
    }

    public void setCurrentWinner(Bidder currentWinner) {
        this.currentWinner = currentWinner;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
