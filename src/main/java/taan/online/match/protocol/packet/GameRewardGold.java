package taan.online.match.protocol.packet;

public record GameRewardGold(
    String label,
    long characterId,
    long amount,
    int hasExtraCard,
    long extraCardAmount
) implements GameReward {
    public static final int TYPE_ID = 0x27a70565;
}
