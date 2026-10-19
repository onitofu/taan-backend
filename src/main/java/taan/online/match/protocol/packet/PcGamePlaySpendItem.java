package taan.online.match.protocol.packet;

import java.util.List;

public record PcGamePlaySpendItem(int slotIndex, int teamClear11, int teamClear12, List<SpentItem> items) {
    public static final int TYPE_ID = 0x4e8107ab;

    public PcGamePlaySpendItem {
        items = List.copyOf(items);
    }
}
