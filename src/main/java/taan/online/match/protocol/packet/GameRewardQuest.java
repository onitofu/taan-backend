package taan.online.match.protocol.packet;

public record GameRewardQuest(String label, long characterId, long value) implements GameReward {
    public static final int TYPE_ID = 0x2dd305f1;

    @Override
    public long amount() {
        return 0;
    }
}
