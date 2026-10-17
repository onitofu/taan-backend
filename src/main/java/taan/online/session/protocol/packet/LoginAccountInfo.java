package taan.online.session.protocol.packet;

public record LoginAccountInfo(String login, String credential) {
    public static final int TYPE_ID = 0x34b00652;

    @Override
    public String toString() {
        return "LoginAccountInfo[REDACTED]";
    }
}
