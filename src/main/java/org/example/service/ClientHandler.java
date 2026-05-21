package org.example.service;

import org.example.dao.BidDAO;
import org.example.model.Auction;
import org.example.model.User;
import org.example.model.Bidder;
import org.example.observer.AuctionNotifier;
import org.example.observer.AuctionObserver;
import org.example.observer.BidUpdateEvent;

import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * Xử lý một client kết nối — chạy trên Thread riêng.
 * Implements AuctionObserver để nhận thông báo real-time từ AuctionNotifier.
 *
 * FIX BUG 8: Đồng nhất protocol — dùng ObjectOutputStream/ObjectInputStream
 *            nhất quán với cách server ghi dữ liệu.
 * FIX BUG 5: Bỏ đoạn broadcast thừa trong case BID — AuctionService đã broadcast bên trong.
 */
public class ClientHandler implements Runnable, AuctionObserver {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final UserService userService;
    private final AuctionService auctionService;
    private final AuctionNotifier auctionNotifier;
    private final BidDAO bidDAO;

    // Lưu user đang đăng nhập — dùng để ghi bid đúng userId vào DB
    private User loggedInUser;

    public ClientHandler(Socket socket, UserService userService,
                         AuctionService auctionService, AuctionNotifier auctionNotifier,
                         BidDAO bidDAO) {
        this.socket = socket;
        this.userService = userService;
        this.auctionService = auctionService;
        this.auctionNotifier = auctionNotifier;
        this.bidDAO = bidDAO;
    }

    @Override
    public void run() {
        try {
            // LUÔN tạo Output trước Input để tránh Deadlock TCP
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Đăng ký nhận thông báo real-time
            auctionNotifier.addObserver(this);

            Object incomingData;
            while ((incomingData = in.readObject()) != null) {
                if (incomingData instanceof String) {
                    String command = (String) incomingData;
                    System.out.println("[Server] Nhận lệnh từ client " + socket.getInetAddress() + ": " + command);
                    handleCommand(command);
                }
            }
        } catch (EOFException e) {
            System.out.println("[Server] Client " + socket.getInetAddress() + " ngắt kết nối.");
        } catch (Exception e) {
            System.out.println("[Server] Lỗi kết nối với client: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleCommand(String command) {
        try {
            if ("VIEW_ITEMS".equals(command)) {
                handleViewItems();

            } else if (command.startsWith("LOGIN")) {
                handleLogin(command);

            } else if (command.startsWith("REGISTER")) {
                handleRegister(command);

            } else if (command.startsWith("CREATE_AUCTION")) {
                handleCreateAuction(command);

            } else if (command.startsWith("BID")) {
                handleBid(command);

            } else {
                sendResponse("FAIL|Không hiểu lệnh: " + command);
            }
        } catch (IOException e) {
            System.err.println("[Server] Lỗi gửi response: " + e.getMessage());
        }
    }

    private void handleViewItems() throws IOException {
        List<Auction> auctions = auctionService.getActiveAuctionsList();
        StringBuilder sb = new StringBuilder("AUCTIONS");
        for (Auction a : auctions) {
            String title = "Không rõ sản phẩm";

            if (a.getItem() != null && a.getItem().getTitle() != null) {
                title = a.getItem().getTitle();
            }

            sb.append("|").append(a.getAuctionId())
                    .append(",").append(title)
                    .append(",").append(a.getCurrentHighestBid())
                    .append(",").append(a.getStatus());
        }
        sendResponse(sb.toString());
    }

    private void handleLogin(String command) throws IOException {
        String[] parts = command.split("\\|");
        if (parts.length == 3) {
            User user = userService.login(parts[1], parts[2]);
            if (user != null) {
                loggedInUser = user;
                sendResponse("SUCCESS|Chào mừng " + user.getUsername() + "! Role: " + user.getRole());
            } else {
                sendResponse("FAIL|Sai tài khoản hoặc mật khẩu");
            }
        } else {
            sendResponse("FAIL|Sai format. Dùng: LOGIN|username|password");
        }
    }

    private void handleRegister(String command) throws IOException {
        String[] parts = command.split("\\|");
        if (parts.length == 4) {
            boolean ok = userService.register(parts[1], parts[2], parts[3]);
            sendResponse(ok ? "SUCCESS|Đăng ký thành công" : "FAIL|Username đã tồn tại");
        } else {
            sendResponse("FAIL|Sai format. Dùng: REGISTER|username|password|role");
        }
    }
    /**
     * Seller/Admin tạo phiên đấu giá mới.
     *
     * Format:
     * CREATE_AUCTION|title|startingPrice|endTime
     *
     * Ví dụ:
     * CREATE_AUCTION|Laptop Gaming|1000|2026-12-31T23:59
     */
    private void handleCreateAuction(String command) throws IOException {
        // 1. Kiểm tra đăng nhập
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước khi tạo phiên đấu giá");
            return;
        }

        // 2. Kiểm tra quyền tạo phiên
        String role = loggedInUser.getRole();

        if (!"ADMIN".equalsIgnoreCase(role)
                && !"USER".equalsIgnoreCase(role)) {
            sendResponse("FAIL|Chỉ Seller hoặc Admin mới được tạo phiên đấu giá");
            return;
        }

        // 3. Tách tham số
        String[] parts = command.split("\\|", -1);

        if (parts.length != 4) {
            sendResponse("FAIL|Sai format. Dùng: CREATE_AUCTION|title|startingPrice|endTime");
            return;
        }

        try {
            String title = parts[1].trim();
            double startingPrice = Double.parseDouble(parts[2].trim());
            String endTime = parts[3].trim();

            if (title.isEmpty()) {
                sendResponse("FAIL|Tên sản phẩm không được để trống");
                return;
            }

            if (startingPrice < 0) {
                sendResponse("FAIL|Giá khởi điểm không được âm");
                return;
            }

            boolean success = auctionService.createAuctionWithNewItem(
                    title,
                    "",
                    startingPrice,
                    endTime,
                    loggedInUser.getId()
            );

            if (success) {
                sendResponse("SUCCESS|Phiên đấu giá đã được tạo thành công");
            } else {
                sendResponse("FAIL|Tạo phiên đấu giá thất bại. Kiểm tra dữ liệu nhập.");
            }

        } catch (NumberFormatException e) {
            sendResponse("FAIL|Giá khởi điểm không hợp lệ");
        }
    }
    private void handleBid(String command) throws IOException {
        // Format: BID|auctionId|amount
        String[] parts = command.split("\\|");
        if (parts.length != 3) {
            sendResponse("FAIL|Sai format. Dùng: BID|auctionId|amount");
            return;
        }

        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước khi đặt giá");
            return;
        }

        try {
            String auctionId = parts[1];
            double amount = Double.parseDouble(parts[2]);
            String bidderName = loggedInUser.getUsername();

            // FIX BUG 5: Chỉ gọi placeBid() — broadcast đã được thực hiện BÊN TRONG AuctionService.
            //            Không tạo thêm event hay gọi broadcast() ở đây nữa.
            boolean success = auctionService.placeBid(auctionId, bidderName, amount);

            if (success) {
                // Ghi lịch sử bid vào DB (dùng userId thật)
                bidDAO.placeBid(Integer.parseInt(auctionId), loggedInUser.getId(), amount);
                sendResponse("SUCCESS|Đặt giá " + amount + " thành công!");
            } else {
                sendResponse("FAIL|Đặt giá thất bại. Giá quá thấp hoặc phiên đã đóng.");
            }

        } catch (NumberFormatException e) {
            sendResponse("FAIL|Số tiền không hợp lệ");
        }
    }

    /** Nhận event real-time từ AuctionNotifier, đẩy ngay về client */
    @Override
    public void onBidUpdate(BidUpdateEvent event) {
        /*
         * Tạm thời không gửi BidUpdateEvent object về JavaFX client.
         *
         * UI hiện đang chờ response dạng String:
         * SUCCESS|...
         * FAIL|...
         *
         * Nếu gửi object event xen vào, client dễ đọc sai hoặc báo lỗi kết nối.
         * Vì vậy chỉ log ra console server để biết có update xảy ra.
         */
        System.out.println("[Notifier] Có bid update, tạm thời không gửi realtime object về client.");
    }
    private void sendResponse(String message) throws IOException {
        out.writeObject(message);
        out.flush();
    }

    private void cleanup() {
        try {
            auctionNotifier.removeObserver(this);
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}