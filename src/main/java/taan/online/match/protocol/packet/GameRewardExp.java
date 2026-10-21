package taan.online.match.protocol.packet;

public record GameRewardExp(String label, long characterId, long amount) implements GameReward {
    public static final int TYPE_ID = 0x2252050c;
}
