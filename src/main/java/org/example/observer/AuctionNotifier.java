package org.example.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Quản lý danh sách Observer và phát sóng sự kiện đấu giá real-time.
 *
 * Bỏ "implements AuctionObserver" — Notifier KHÔNG phải Observer.
 *            Notifier là subject/publisher trong Observer Pattern.
 *
 * Thay new Thread() bằng ExecutorService có giới hạn thread pool
 *             để tránh OOM khi có nhiều client kết nối đồng thời.
 */
public class AuctionNotifier {

    // CopyOnWriteArrayList: an toàn khi đọc/ghi đồng thời, tránh ConcurrentModificationException
    private final List<AuctionObserver> observers = new CopyOnWriteArrayList<>();

    // ThreadPool cố định 10 thread — đủ cho broadcast, không tạo thread vô hạn
    private final ExecutorService broadcastPool = Executors.newFixedThreadPool(10);

    /** Client đăng ký nhận thông báo real-time */
    public void addObserver(AuctionObserver observer) {
        observers.add(observer);
        System.out.println("[Notifier] Client đăng ký. Tổng observer: " + observers.size());
    }

    /** Client ngắt kết nối, hủy đăng ký */
    public void removeObserver(AuctionObserver observer) {
        observers.remove(observer);
        System.out.println("[Notifier] Client rời. Còn lại: " + observers.size());
    }

    /**
     * Phát sự kiện đến tất cả observer đang kết nối.
     * Mỗi observer được notify trên một task trong ThreadPool (không block lẫn nhau).
     */
    public void broadcast(BidUpdateEvent event) {
        System.out.println("[Notifier] Broadcast tới " + observers.size() + " observer(s).");
        for (AuctionObserver observer : observers) {
            broadcastPool.submit(() -> {
                try {
                    observer.onBidUpdate(event);
                } catch (Exception e) {
                    System.err.println("[Notifier] Lỗi gửi event tới observer: " + e.getMessage());
                    // Tự động remove observer bị lỗi (client mất kết nối)
                    removeObserver(observer);
                }
            });
        }
    }

    /**
     * Đóng ThreadPool khi server shutdown.
     * Nên gọi trong shutdown hook của AuctionServer.
     */
    public void shutdown() {
        broadcastPool.shutdown();
        System.out.println("[Notifier] Đóng thông báo");
    }

    /** @deprecated Dùng addObserver() thay thế */
    @Deprecated
    public void addObserve(AuctionObserver observer) { addObserver(observer); }

    /** @deprecated Dùng removeObserver() thay thế */
    @Deprecated
    public void removeObserve(AuctionObserver observer) { removeObserver(observer); }
}
