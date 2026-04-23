package org.example.model;

public class Item {
    private int id;
    private String title;
    private String description;
    private double startingPrice;
    private double currentPrice;
    private String endTime;
    private int sellerId;
    private String status;

    public Item() {}

    public Item(int id, String title, String description, double startingPrice, double currentPrice, String endTime, int sellerId, String status) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.endTime = endTime;
        this.sellerId = sellerId;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }
    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "Item{" + "id=" + id + ", title='" + title + '\'' + ", currentPrice=" + currentPrice + ", status='" + status + '\'' + '}';
    }
}