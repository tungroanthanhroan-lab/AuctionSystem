package org.example.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuctionNotifier implements AuctionObserver {
    //danh sach cac luong ket noi cua client dang xem phien dau gia
    //CopyOnWriteList dam bao khong bi loi ConcurretModificationException
    private final List<AuctionObserver> observes = new CopyOnWriteArrayList<>();

    @Override
    public void onBidUpdate(BidUpdateEvent event) {
        System.out.println("Phien dau gia " + event.getAuctionId() + " da duoc " + event.getBidder() + " dat gia " + event.getNewHighestAmount() + " vao luc " + event.getTimestamp());
    }
    //fronend qua luong socket xin nhan thong bao
    public void addObserve(AuctionObserver observe) {
        observes.add(observe);
    }

    //frontend roi khoi phien dau gia
    public void removeObserve(AuctionObserver observe) {
        observes.remove(observe);
    }

    //gui thong bao cho tat ca nguoi dang xem
    public void broadcast(BidUpdateEvent event) {
        for (AuctionObserver observe : observes) {
            //day ra mot luong rieng de tranh 1 client mang cham lam cham ca danh sach
            new Thread(() -> observe.onBidUpdate(event)).start();
        }
    }
}
