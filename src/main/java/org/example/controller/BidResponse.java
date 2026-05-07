package org.example.controller;

public class BidResponse {
    private boolean success;
    private String msg;
    private double currentHighestBid;

    public BidResponse(boolean success, String msg, double currentHighestBid) {
        this.success = success;
        this.msg = msg;
        this.currentHighestBid = currentHighestBid;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public double getCurrentHighestBid() {
        return currentHighestBid;
    }

    public void setCurrentHighestBid(double currentHighestBid) {
        this.currentHighestBid = currentHighestBid;
    }
}
