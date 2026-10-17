package taan.online.character.protocol.packet;

import java.util.List;
import taan.online.inventory.protocol.packet.Inventory;

public record PrSelectCharacter(CharacterInfo character, List<Inventory> inventories) {
    public static final int TYPE_ID = 0x3acb06af;

    public PrSelectCharacter {
        inventories = List.copyOf(inventories);

        if (inventories.size() != 3) {
            throw new IllegalArgumentException("Selection requires three inventories");
        }
    }
}
