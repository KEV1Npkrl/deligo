package pe.deligo.shared;

public class BusinessException extends RuntimeException {
    private final int status;
    public BusinessException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
    public static void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(409, message);
    }
}
