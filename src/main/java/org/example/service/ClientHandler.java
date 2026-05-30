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

/**
 * ClientHandler — processes one client connection per thread.
 *
 * MERGE NOTES:
 *  - Command set from master (full UI): VIEW_ITEMS, LOGIN, REGISTER, BID, CREATE_AUCTION,
 *    CLOSE_AUCTION, GET_BID_HISTORY, MY_AUCTIONS, CHANGE_PASSWORD, DEPOSIT, CHECK_BALANCE.
 *  - onBidUpdate() from rebuild: actually sends BidUpdateEvent objects over the socket,
 *    so the CLI AuctionClient can print real-time updates.
 *    The master version suppressed this to avoid mixing object types on the stream.
 *    Since BidUpdateEvent now uses a String auctionId (not Auction), serialization is safe.
 *  - handleViewItems() uses the richer format from master (includes title, leader, endTime).
 */
public class ClientHandler implements Runnable, AuctionObserver {

    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final UserService userService;
    private final AuctionService auctionService;
    private final AuctionNotifier auctionNotifier;
    private final BidDAO bidDAO;

    private User loggedInUser;

    public ClientHandler(Socket socket, UserService userService,
                         AuctionService auctionService, AuctionNotifier auctionNotifier,
                         BidDAO bidDAO) {
        this.socket          = socket;
        this.userService     = userService;
        this.auctionService  = auctionService;
        this.auctionNotifier = auctionNotifier;
        this.bidDAO          = bidDAO;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());

            auctionNotifier.addObserver(this);

            Object incomingData;
            while ((incomingData = in.readObject()) != null) {
                if (incomingData instanceof String) {
                    String command = (String) incomingData;
                    System.out.println("[Server] Lệnh từ " + socket.getInetAddress() + ": " + command);
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
                handleCheckBalance();
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
            String leader  = (a.getCurrentLeader() != null) ? a.getCurrentLeader().getUsername() : "-";
            String endTime = (a.getEndTime() != null) ? a.getEndTime().toString() : "-";

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
            String auctionId  = parts[1].trim();
            double amount     = Double.parseDouble(parts[2].trim());
            String bidderName = loggedInUser.getUsername();

            if (auctionService.isAuctionOwner(auctionId, loggedInUser.getId())) {
                sendResponse("FAIL|Bạn không thể đấu giá phiên do chính mình tạo");
                return;
            }

            double availableBalance = userService.getAvailableBalance(bidderName);
            if (availableBalance < amount) {
                sendResponse("FAIL|Đặt giá thất bại. Số dư khả dụng không đủ: "
                        + String.format("%.2f", availableBalance)
                        + " (cần: " + String.format("%.2f", amount) + ")");
                return;
            }

            boolean success = auctionService.placeBid(auctionId, bidderName, amount);
            if (success) {
                bidDAO.placeBid(Integer.parseInt(auctionId), loggedInUser.getId(), amount);
                double newAvailable = userService.getAvailableBalance(bidderName);
                sendResponse("SUCCESS|Đặt giá " + amount + " thành công!"
                        + " | Số dư khả dụng còn: " + String.format("%.2f", newAvailable));
            } else {
                sendResponse("FAIL|Đặt giá thất bại. Giá quá thấp, phiên đã đóng, hoặc xung đột đồng thời.");
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
        boolean isAdmin  = "ADMIN".equalsIgnoreCase(loggedInUser.getRole());
        boolean isOwner  = auctionService.isAuctionOwner(auctionId, loggedInUser.getId());
        if (!isAdmin && !isOwner) {
            sendResponse("FAIL|Bạn chỉ có thể đóng phiên do chính mình tạo");
            return;
        }
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
        try {
            String auctionId = parts[1].trim();
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
            String title   = (a.getItem() != null && a.getItem().getTitle() != null)
                    ? a.getItem().getTitle() : "Không rõ sản phẩm";
            String leader  = (a.getCurrentLeader() != null) ? a.getCurrentLeader().getUsername() : "-";
            String endTime = (a.getEndTime() != null) ? a.getEndTime().toString() : "-";
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
        sendResponse(success ? "SUCCESS|Đổi mật khẩu thành công" : "FAIL|Sai mật khẩu cũ");
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
            sendResponse(success ? "SUCCESS|Nạp tiền thành công" : "FAIL|Có lỗi xảy ra khi nạp tiền");
        } catch (NumberFormatException e) {
            sendResponse("FAIL|Số tiền không hợp lệ");
        }
    }

    private void handleCheckBalance() throws IOException {
        if (loggedInUser == null) {
            sendResponse("FAIL|Bạn cần đăng nhập trước");
            return;
        }
        String username  = loggedInUser.getUsername();
        double balance   = userService.getBalance(username);
        double held      = userService.getHeldBalance(username);
        double available = userService.getAvailableBalance(username);

        if (balance >= 0) {
            sendResponse("BALANCE|" + balance + "|" + held + "|" + available);
        } else {
            sendResponse("FAIL|Lỗi khi lấy số dư");
        }
    }

    /**
     * Real-time notification — from rebuild.
     * Sends the BidUpdateEvent object directly to the client so CLI AuctionClient
     * and (future) push-based UI clients can receive live updates.
     *
     * MERGE NOTE: Master suppressed this to avoid stream confusion. Since BidUpdateEvent
     * now stores auctionId as String (not Auction), it is safe to serialize. The JavaFX
     * UI uses polling (auto-refresh every 2 s) so it ignores incoming objects gracefully
     * via NetworkService.readStringResponse().
     */
    @Override
    public void onBidUpdate(BidUpdateEvent event) {
        try {
            out.writeObject(event);
            out.flush();
        } catch (IOException e) {
            System.out.println("[Notifier] Không gửi được event → tự động remove client.");
            auctionNotifier.removeObserver(this);
        }
    }

    private void sendResponse(String message) throws IOException {
        out.writeObject(message);
        out.flush();
    }

    private void cleanup() {
        try {
            auctionNotifier.removeObserver(this);
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}