package taan.online.match.protocol.packet;

public sealed interface GameReward permits GameRewardExp, GameRewardGold, GameRewardQuest {
    String label();

    long characterId();

    long amount();
}
