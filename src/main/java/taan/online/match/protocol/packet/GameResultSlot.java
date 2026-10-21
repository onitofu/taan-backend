package taan.online.match.protocol.packet;

import java.util.List;

public record GameResultSlot(
    int slotIndex,
    int level,
    long expDeltaFallback,
    long displayedGold,
    int kills,
    long damage,
    float accuracy,
    long assists,
    List<Integer> medals,
    int showSoulBonus,
    long soulExp,
    long soulGold
) {
    public static final int TYPE_ID = 0x287c059b;

    public GameResultSlot {
        medals = List.copyOf(medals);
    }
}
