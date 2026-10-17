package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.PrSelectCharacter;
import taan.online.inventory.protocol.codec.InventoryCodec;
import taan.online.protocol.BinaryWriter;

public final class SelectCharacterResponseCodec {
    public byte[] encode(PrSelectCharacter response) {
        var output = new BinaryWriter().unsignedInt(PrSelectCharacter.TYPE_ID).unsignedInt(1);
        new CharacterInfoCodec().encode(output, response.character());

        for (var inventory : response.inventories()) {
            output.unsignedInt(1).bytes(new InventoryCodec().encode(inventory));
        }

        return output.unsignedInt(0).unsignedInt(0).toByteArray();
    }
}
