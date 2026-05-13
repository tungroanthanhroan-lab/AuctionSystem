package org.example.observer;

public interface AuctionObserver {
    //ham nay se duoc goi khi cap nhat gia moi
    void onBidUpdate(BidUpdateEvent event);
}