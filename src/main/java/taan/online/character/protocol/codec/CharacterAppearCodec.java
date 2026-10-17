package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.CharacterAppear;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class CharacterAppearCodec {
    public CharacterAppear decode(BinaryReader input) {
        if (input.unsignedInt() != Integer.toUnsignedLong(CharacterAppear.TYPE_ID)) {
            throw new MalformedPacketException("UNEXPECTED_APPEARANCE_TYPE");
        }

        var appearance = new CharacterAppear(
            input.unsignedByte(),
            input.unsignedInt(),
            input.unsignedShort(),
            new AppearancePartsCodec().decode(input)
        );

        if (input.unsignedByte() != 0) {
            throw new MalformedPacketException("UNSUPPORTED_APPEARANCE_OPTIONS");
        }

        return appearance;
    }

    public void encode(BinaryWriter output, CharacterAppear appearance) {
        output.unsignedInt(CharacterAppear.TYPE_ID).unsignedByte(appearance.mode())
            .unsignedInt(appearance.opaque08()).unsignedShort(appearance.opaque0c());
        new AppearancePartsCodec().encode(output, appearance.parts());
        output.unsignedByte(0);
    }
}
