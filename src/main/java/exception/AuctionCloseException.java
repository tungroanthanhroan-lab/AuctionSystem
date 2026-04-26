package exception;
// tu tao loi
// cac loi logic

public class AuctionCloseException extends RuntimeException {
    public AuctionCloseException(String msg) {
        super(msg);
    }
}
