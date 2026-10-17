package taan.online.character.protocol.codec;

import java.util.ArrayList;
import java.util.List;
import taan.online.character.protocol.packet.CharacterPart;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

final class AppearancePartsCodec {
    List<CharacterPart> decode(BinaryReader input) {
        var parts = new ArrayList<CharacterPart>();

        for (int slot = 0; slot < 16; slot++) {
            parts.add(
                new CharacterPart(
                    input.unsignedInt(),
                    input.unsignedShort(),
                    input.unsignedShort(),
                    input.unsignedShort()
                )
            );
        }

        return List.copyOf(parts);
    }

    void encode(BinaryWriter output, List<CharacterPart> parts) {
        for (var part : parts) {
            output.unsignedInt(part.itemId()).unsignedShort(part.parameter0())
                .unsignedShort(part.parameter1()).unsignedShort(part.parameter2());
        }
    }
}
