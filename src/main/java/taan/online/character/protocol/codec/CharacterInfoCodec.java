package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.CharacterInfo;
import taan.online.protocol.BinaryWriter;

final class CharacterInfoCodec {
    void encode(BinaryWriter output, CharacterInfo info) {
        output.unsignedInt(CharacterInfo.TYPE_ID).unsignedInt(info.characterId())
            .wideString(info.name()).unsignedShort(info.level())
            .unsignedInt(0).unsignedInt(0).unsignedInt(0).unsignedInt(info.gold()).unsignedByte(info.gender());

        for (int field = 0; field < 9; field++) {
            output.unsignedInt(0);
        }

        output.wideString("");
    }
}
