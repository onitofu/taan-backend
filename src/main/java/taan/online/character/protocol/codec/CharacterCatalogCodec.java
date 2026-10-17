package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.CharacterCatalog;
import taan.online.protocol.BinaryWriter;

public final class CharacterCatalogCodec {
    public byte[] encode(CharacterCatalog catalog) {
        var output = new BinaryWriter().unsignedInt(CharacterCatalog.TYPE_ID).unsignedInt(catalog.info().size());

        for (var info : catalog.info()) {
            new CharacterInfoCodec().encode(output, info);
        }

        output.unsignedInt(catalog.appearances().size());

        for (var appearance : catalog.appearances()) {
            new CharacterAppearCodec().encode(output, appearance);
        }

        return output.toByteArray();
    }
}
