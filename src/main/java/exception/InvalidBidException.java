package exception;
//loi khi dat gia khong hop le
public class InvalidBidException extends RuntimeException {
    public InvalidBidException(String msg) {
        super(msg);
    }
}
