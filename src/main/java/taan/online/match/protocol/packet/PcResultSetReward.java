package taan.online.match.protocol.packet;

import java.util.List;

public record PcResultSetReward(List<GameReward> rewards) {
    public static final int TYPE_ID = 0x42a60722;

    public PcResultSetReward {
        rewards = List.copyOf(rewards);
    }
}
