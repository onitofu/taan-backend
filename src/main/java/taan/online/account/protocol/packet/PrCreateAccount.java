package taan.online.account.protocol.packet;

public record PrCreateAccount(int status, int selector) {
    public static final int TYPE_ID = 0x2d7b05e3;
}
