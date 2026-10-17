package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.CharacterAppear;
import taan.online.protocol.BinaryWriter;

final class CharacterAppearCodec {
    void encode(BinaryWriter output, CharacterAppear appearance) {
        output.unsignedInt(CharacterAppear.TYPE_ID).unsignedByte(appearance.mode())
            .unsignedInt(appearance.opaque08()).unsignedShort(appearance.opaque0c());
        new AppearancePartsCodec().encode(output, appearance.parts());
        output.unsignedByte(0);
    }
}
