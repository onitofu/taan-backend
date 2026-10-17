package taan.online.character.protocol.packet;

import java.util.List;

public record PqNewCharacter(int localSlot, String name, List<CharacterPart> parts) {
    public static final int TYPE_ID = 0x27b90578;

    public PqNewCharacter {
        parts = List.copyOf(parts);
    }

    @Override
    public String toString() {
        return "PqNewCharacter[REDACTED]";
    }
}
