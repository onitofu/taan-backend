package taan.online.account.protocol.packet;

public record PqCreateAccount(String login, String credential, int gender, int selector) implements AccountRequest {
    public static final int TYPE_ID = 0x2d6d05e2;

    @Override
    public String toString() {
        return "PqCreateAccount[REDACTED]";
    }
}
