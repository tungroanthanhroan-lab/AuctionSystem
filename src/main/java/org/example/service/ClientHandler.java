package org.example.service;

import org.example.dao.BidDAO;
import org.example.model.Auction;
import org.example.model.User;
import org.example.observer.AuctionNotifier;
import org.example.observer.AuctionObserver;
import org.example.observer.BidUpdateEvent;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable, AuctionObserver {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final UserService userService;
    private final AuctionService auctionService;
    private final AuctionNotifier auctionNotifier;
    private final BidDAO bidDAO;

    private User loggedInUser;

    public ClientHandler(Socket socket,
                         UserService userService,
                         AuctionService auctionService,
                         AuctionNotifier auctionNotifier,
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
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

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

            } else if (command.startsWith("CLOSE_AUCTION")) {
                handleCloseAuction(command);

            } else if (command.startsWith("GET_BID_HISTORY")) {
                handleGetBidHistory(command);

            } else if (command.startsWith("MY_AUCTIONS")) {
                handleMyAuctions();

            } else if (command.startsWith("CHANGE_PASSWORD")) {
                handleChangePassword(command);

            } else if (command.startsWith("DEPOSIT")) {
                handleDeposit(command);

            } else if (command.startsWith("CHECK_BALANCE")) {
                handleCheckBalance(command);

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

            String leader = (a.getCurrentLeader() != null)
                    ? a.getCurrentLeader().getUsername()
                    : "-";

            String endTime = (a.getEndTime() != null)
                    ? a.getEndTime().toString()
                    : "-";

            sb.append("|")
                    .append(a.getAuctionId()).append(",")
                    .append(title).append(",")
                    .append(a.getCurrentHighestBid()).append(",")
                    .append(a.getStatus()).append(",")
                    .append(leader).append(",")
                    .append(endTime);
        }

        sendResponse(sb.toString());
    }

    private void handleLogin(String command) throws IOException {
        String[] parts = command.split("\\|");
        if (parts.length != 3) {
            sendResponse("FAIL|Sai format. Dùng: LOGIN|username|password");
            return;
        }

        User user = userService.login(parts[1], parts[2]);
        if (user != null) {
            loggedInUser = user;
            sendResponse("SUCCESS|Chào mừng " + user.getUsername() + "! Role: " + user.getRole());
        } else {
            sendResponse("FAIL|Sai tài khoản hoặc mật khẩu");
        }
    }

    private void handleRegister(String command) throws IOException {
        String[] parts = command.split("\\|");
        if (parts.length != 4) {
            sendResponse("FAIL|Sai format. Dùng: REGISTER|username|password|role");
            return;
        }

        boolean ok = userService.register(parts[1], parts[2], parts[3]);
        sendResponse(ok ? "SUCCESS|Đăng ký thành công" : "FAIL|Username đã tồn tại");
    }

    private void handleCreateAuction(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước khi tạo phiên đấu giá");
            return;
        }

        String role = loggedInUser.getRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"USER".equalsIgnoreCase(role)) {
            sendResponse("FAIL|Chỉ User hoặc Admin mới được tạo phiên đấu giá");
            return;
        }

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
                    title, "", startingPrice, endTime, loggedInUser.getId());

            sendResponse(success
                    ? "SUCCESS|Phiên đấu giá đã được tạo thành công"
                    : "FAIL|Tạo phiên đấu giá thất bại. Kiểm tra dữ liệu nhập.");

        } catch (NumberFormatException e) {
            sendResponse("FAIL|Giá khởi điểm không hợp lệ");
        }
    }

    private void handleBid(String command) throws IOException {
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
            String auctionId = parts[1].trim();
            double amount = Double.parseDouble(parts[2].trim());
            String bidderName = loggedInUser.getUsername();

            if (auctionService.isAuctionOwner(auctionId, loggedInUser.getId())) {
                sendResponse("FAIL|Bạn không thể đấu giá phiên do chính mình tạo");
                return;
            }

            // HOLD BALANCE: Kiểm tra available_balance trước khi gửi xuống AuctionService
            // available_balance = balance - held_balance
            // Giúp trả về thông báo lỗi rõ ràng cho client ngay tại đây,
            // thay vì để AuctionService reject mà không giải thích lý do.
            double availableBalance = userService.getAvailableBalance(bidderName);
            if (availableBalance < amount) {
                sendResponse("FAIL|Đặt giá thất bại. Số dư khả dụng không đủ: "
                        + String.format("%.2f", availableBalance)
                        + " (cần: " + String.format("%.2f", amount) + ")");
                return;
            }

            // AuctionService.placeBid() sẽ thực hiện hold tiền + update auction trong 1 DB transaction
            boolean success = auctionService.placeBid(auctionId, bidderName, amount);

            if (success) {
                // Ghi vào bảng bids để lưu lịch sử đấu giá (audit trail)
                bidDAO.placeBid(Integer.parseInt(auctionId), loggedInUser.getId(), amount);

                // Trả về cả available_balance mới sau khi hold để client có thể hiển thị
                double newAvailable = userService.getAvailableBalance(bidderName);
                sendResponse("SUCCESS|Đặt giá " + amount + " thành công!"
                        + " | Số dư khả dụng còn: " + String.format("%.2f", newAvailable));
            } else {
                sendResponse("FAIL|Đặt giá thất bại. Giá quá thấp, phiên đã đóng, hoặc xảy ra xung đột đồng thời.");
            }

        } catch (NumberFormatException e) {
            sendResponse("FAIL|Số tiền không hợp lệ");
        }
    }

    private void handleCloseAuction(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước khi đóng phiên");
            return;
        }

        String[] parts = command.split("\\|");
        if (parts.length != 2) {
            sendResponse("FAIL|Sai format. Dùng: CLOSE_AUCTION|auctionId");
            return;
        }

        String auctionId = parts[1].trim();

        boolean isAdmin = "ADMIN".equalsIgnoreCase(loggedInUser.getRole());
        boolean isOwner = auctionService.isAuctionOwner(auctionId, loggedInUser.getId());

        if (!isAdmin && !isOwner) {
            sendResponse("FAIL|Bạn chỉ có thể đóng phiên do chính mình tạo");
            return;
        }

        // HOLD BALANCE: closeAuction() sẽ tự xử lý:
        //   - Trừ balance thật của winner + release held
        //   - Cộng tiền cho seller
        //   - Những bidder thua không cần xử lý (held đã được release từng lần bị vượt giá)
        boolean success = auctionService.closeAuction(auctionId);

        if (success) {
            sendResponse("SUCCESS|Phiên đấu giá đã được đóng thành công. Tiền đã được thanh toán cho winner và seller.");
        } else {
            sendResponse("FAIL|Đóng phiên thất bại hoặc phiên không tồn tại");
        }
    }

    private void handleGetBidHistory(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước khi xem lịch sử đấu giá");
            return;
        }

        String[] parts = command.split("\\|");
        if (parts.length != 2) {
            sendResponse("FAIL|Sai format. Dùng: GET_BID_HISTORY|auctionId");
            return;
        }

        String auctionId = parts[1].trim();

        try {
            List<String[]> history = bidDAO.getBidHistory(Integer.parseInt(auctionId));

            StringBuilder sb = new StringBuilder("BID_HISTORY|").append(auctionId);

            for (String[] row : history) {
                sb.append("|")
                        .append(row[0]).append(",")
                        .append(row[1]).append(",")
                        .append(row[2]).append(",")
                        .append(row[3]);
            }

            sendResponse(sb.toString());

        } catch (NumberFormatException e) {
            sendResponse("FAIL|auctionId không hợp lệ");
        }
    }

    private void handleMyAuctions() throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước khi xem phiên của tôi");
            return;
        }

        List<Auction> auctions = auctionService.getAuctionsBySellerId(loggedInUser.getId());

        StringBuilder sb = new StringBuilder("MY_AUCTIONS");

        for (Auction a : auctions) {
            String title = "Không rõ sản phẩm";
            if (a.getItem() != null && a.getItem().getTitle() != null) {
                title = a.getItem().getTitle();
            }

            String leader = (a.getCurrentLeader() != null)
                    ? a.getCurrentLeader().getUsername()
                    : "-";

            String endTime = (a.getEndTime() != null)
                    ? a.getEndTime().toString()
                    : "-";

            sb.append("|")
                    .append(a.getAuctionId()).append(",")
                    .append(title).append(",")
                    .append(a.getCurrentHighestBid()).append(",")
                    .append(a.getStatus()).append(",")
                    .append(leader).append(",")
                    .append(endTime);
        }

        sendResponse(sb.toString());
    }

    private void handleChangePassword(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước");
            return;
        }

        String[] parts = command.split("\\|");
        if (parts.length != 3) {
            sendResponse("FAIL|Sai format. Dùng: CHANGE_PASSWORD|oldPass|newPass");
            return;
        }

        boolean success = userService.changePassword(loggedInUser.getUsername(), parts[1], parts[2]);

        if (success) {
            sendResponse("SUCCESS|Đổi mật khẩu thành công");
        } else {
            sendResponse("FAIL|Sai mật khẩu cũ");
        }
    }

    private void handleDeposit(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước");
            return;
        }

        String[] parts = command.split("\\|");
        if (parts.length != 2) {
            sendResponse("FAIL|Sai format. Dùng: DEPOSIT|amount");
            return;
        }

        try {
            double amount = Double.parseDouble(parts[1].trim());

            if (amount <= 0) {
                sendResponse("FAIL|Số tiền nạp phải lớn hơn 0");
                return;
            }

            boolean success = userService.updateBalance(loggedInUser.getUsername(), amount);

            if (success) {
                sendResponse("SUCCESS|Nạp tiền thành công");
            } else {
                sendResponse("FAIL|Có lỗi xảy ra khi nạp tiền");
            }

        } catch (NumberFormatException e) {
            sendResponse("FAIL|Số tiền không hợp lệ");
        }
    }

    private void handleCheckBalance(String command) throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước");
            return;
        }

        String username = loggedInUser.getUsername();

        // HOLD BALANCE: Trả về cả 3 giá trị để client hiển thị đầy đủ tình trạng tài chính:
        //   balance       — số dư gốc (bao gồm cả phần đang bị hold)
        //   held_balance  — phần đang bị đóng băng cho bid hiện tại
        //   available     — phần có thể dùng để bid mới (= balance - held_balance)
        double balance   = userService.getBalance(username);
        double held      = userService.getHeldBalance(username);
        double available = userService.getAvailableBalance(username);

        if (balance >= 0) {
            // Format: BALANCE|<balance>|<held>|<available>
            // Client có thể parse theo pipe để hiển thị riêng từng mục
            sendResponse("BALANCE|" + balance + "|" + held + "|" + available);
        } else {
            sendResponse("FAIL|Lỗi khi lấy số dư");
        }
    }

    @Override
    public void onBidUpdate(BidUpdateEvent event) {
        /*
         * Không gửi BidUpdateEvent object về JavaFX client.
         * UI đang dùng request-response String; gửi object xen vào dễ làm client đọc sai.
         */
        System.out.println("[Notifier] Có bid update, UI hiện dùng polling nên không gửi object event.");
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