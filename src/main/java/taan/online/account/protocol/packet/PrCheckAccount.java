package taan.online.account.protocol.packet;

public record PrCheckAccount(int status, int selector) {
    public static final int TYPE_ID = 0x26fe056d;
}
