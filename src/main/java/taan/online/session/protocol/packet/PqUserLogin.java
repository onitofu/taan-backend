package taan.online.session.protocol.packet;

import java.util.List;

public record PqUserLogin(List<LoginAccountInfo> accounts) {
    public static final int TYPE_ID = 0x198b0459;

    public PqUserLogin {
        accounts = List.copyOf(accounts);
    }

    @Override
    public String toString() {
        return "PqUserLogin[REDACTED]";
    }
}
