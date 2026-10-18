package taan.online.match.protocol.packet;

import java.util.List;

public record GameResultSlot(
    int slotIndex,
    int scalar0a,
    long scalar0c,
    long scalar1c,
    int scalar20,
    long scalar24,
    float scalar28,
    long scalar2c,
    List<Integer> scalar3c,
    int scalar3f,
    long scalar40,
    long scalar44
) {
    public static final int TYPE_ID = 0x287c059b;

    public GameResultSlot {
        scalar3c = List.copyOf(scalar3c);
    }
}
