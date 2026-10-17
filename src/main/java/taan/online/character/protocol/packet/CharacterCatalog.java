package taan.online.character.protocol.packet;

import java.util.List;

public record CharacterCatalog(List<CharacterInfo> info, List<CharacterAppear> appearances) {
    public static final int TYPE_ID = 0x343e0648;

    public CharacterCatalog {
        info = List.copyOf(info);
        appearances = List.copyOf(appearances);

        if (info.size() != appearances.size()) {
            throw new IllegalArgumentException("Character catalog vectors must have matching sizes");
        }
    }
}
