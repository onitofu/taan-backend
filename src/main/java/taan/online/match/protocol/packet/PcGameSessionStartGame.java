package taan.online.match.protocol.packet;

import java.util.List;

public record PcGameSessionStartGame(
    int mapId,
    List<GameSlotPlayInfo> players,
    int initialWindRaw,
    int scalar24,
    List<Integer> teamLeaderSlots,
    long scalar2c
) {
    public static final int TYPE_ID = 0x69bd08f8;

    public PcGameSessionStartGame {
        players = List.copyOf(players);
        teamLeaderSlots = List.copyOf(teamLeaderSlots);
    }
}
