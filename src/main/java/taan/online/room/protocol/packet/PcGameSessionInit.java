package taan.online.room.protocol.packet;

import java.util.List;

public record PcGameSessionInit(
    GameSessionInfo info,
    GameSessionOption option,
    List<GameSlotInfo> slots,
    int missionDifficulty,
    String sideName1,
    String sideName2
) {
    public static final int TYPE_ID = 0x40a10704;

    public PcGameSessionInit {
        slots = List.copyOf(slots);
    }
}
