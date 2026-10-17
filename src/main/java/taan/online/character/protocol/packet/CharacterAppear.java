package taan.online.character.protocol.packet;

import java.util.List;

public record CharacterAppear(int mode, long opaque08, int opaque0c, List<CharacterPart> parts) {
    public static final int TYPE_ID = 0x2e1e05e6;

    public CharacterAppear {
        parts = List.copyOf(parts);

        if (parts.size() != 16) {
            throw new IllegalArgumentException("Character appearance requires sixteen wire parts");
        }
    }
}
