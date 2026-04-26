package exception;
//xu li loi du lieu/dong thoi dung khi tich hop DB tranh Lost Update
public class ConcurrentBidException extends RuntimeException{
    public ConcurrentBidException(String msg) {
        super(msg);
    }
}
