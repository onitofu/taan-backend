package taan.online.session.protocol.packet;

import java.util.List;
import java.util.Objects;
import taan.online.character.protocol.packet.CharacterCatalog;

public record PrUserLogin(
    boolean success,
    long accountId,
    int gender,
    int errorCode,
    CharacterCatalog characterCatalog
) {
    public static final int TYPE_ID = 0x1995045a;

    public PrUserLogin {
        Objects.requireNonNull(characterCatalog);
    }

    public PrUserLogin(boolean success, long accountId, int gender, int errorCode) {
        this(success, accountId, gender, errorCode, new CharacterCatalog(List.of(), List.of()));
    }
}
