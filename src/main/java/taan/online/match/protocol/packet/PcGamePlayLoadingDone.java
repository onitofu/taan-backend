package taan.online.match.protocol.packet;

import java.util.List;

public record PcGamePlayLoadingDone(List<LoadingPosition> positions) {
    public static final int TYPE_ID = 0x5e8f0866;

    public PcGamePlayLoadingDone {
        positions = List.copyOf(positions);
    }
}
