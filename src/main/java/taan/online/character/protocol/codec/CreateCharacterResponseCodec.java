package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.PrNewCharacter;
import taan.online.protocol.BinaryWriter;

public final class CreateCharacterResponseCodec {
    public byte[] encode(PrNewCharacter response) {
        var output = new BinaryWriter().unsignedInt(PrNewCharacter.TYPE_ID)
            .unsignedByte(response.result()).unsignedByte(response.localSlot());

        if (response.catalog() == null) {
            output.unsignedByte(0);
        } else {
            output.unsignedByte(1).bytes(new CharacterCatalogCodec().encode(response.catalog()));
        }

        return output.toByteArray();
    }
}
