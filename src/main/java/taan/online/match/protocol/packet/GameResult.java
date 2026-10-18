package taan.online.match.protocol.packet;

import java.util.List;

public record GameResult(int resultKind, List<GameResultSlot> slots, long scalar18, long scalar1c) {
    public static final int TYPE_ID = 0x14b603f9;

    public GameResult {
        slots = List.copyOf(slots);
    }
}
