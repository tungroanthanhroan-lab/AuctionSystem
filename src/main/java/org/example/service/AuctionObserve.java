package org.example.service;

public interface AuctionObserve {
    //ham nay se duoc goi khi cap nhat gia moi
    void onBidUpdate(BidUpdateEvent event);
}
