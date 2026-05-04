package org.example.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuctionNotifier {
    //danh sach cac luong ket noi cua client dang xem phien dau gia
    //CopyOnWriteList dam bao khong bi loi ConcurretModificationException
    private final List<AuctionObserve> observes = new CopyOnWriteArrayList<>();

    //fronend qua luong socket xin nhan thong bao
    public void addObserve(AuctionObserve observe) {
        observes.add(observe);
    }

    //frontend roi khoi phien dau gia
    public void removeObserve(AuctionObserve observe) {
        observes.remove(observe);
    }

    //gui thong bao cho tat ca nguoi dang xem
    public void broadcast(BidUpdateEvent event) {
        for (AuctionObserve observe : observes) {
            //day ra mot luong rieng de tranh 1 client mang cham lam cham ca danh sach
            new Thread(() -> observe.onBidUpdate(event)).start();
        }
    }
}
