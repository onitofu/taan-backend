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
    long soulGold,
    List<ExpResult> expSnapshots,
    List<ResultBonus> bonuses
) {
    public static final int TYPE_ID = 0x287c059b;

    public GameResultSlot(
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
        this(
            slotIndex,
            level,
            expDeltaFallback,
            displayedGold,
            kills,
            damage,
            accuracy,
            assists,
            medals,
            showSoulBonus,
            soulExp,
            soulGold,
            List.of(),
            List.of()
        );
    }

    public GameResultSlot {
        medals = List.copyOf(medals);
        expSnapshots = List.copyOf(expSnapshots);
        bonuses = List.copyOf(bonuses);
    }
}
