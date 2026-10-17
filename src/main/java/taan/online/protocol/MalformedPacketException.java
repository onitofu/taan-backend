package taan.online.protocol;

public final class MalformedPacketException extends RuntimeException {
    public MalformedPacketException(String reason) {
        super(reason);
    }
}
