package org.example.observer;

public interface AuctionObserver {
    // Hàm này sẽ được gọi khi cập nhật giá mới
    void onBidUpdate(BidUpdateEvent event);
}