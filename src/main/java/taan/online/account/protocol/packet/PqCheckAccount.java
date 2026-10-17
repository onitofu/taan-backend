package taan.online.account.protocol.packet;

public record PqCheckAccount(String login, int selector) implements AccountRequest {
    public static final int TYPE_ID = 0x26f1056c;

    @Override
    public String toString() {
        return "PqCheckAccount[REDACTED]";
    }
}
