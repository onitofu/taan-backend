package taan.online.match.model;

import java.util.Objects;

public record MatchRewardCard(int draw, MatchRewardCardKind kind, long amount) {
    public MatchRewardCard {
        Objects.requireNonNull(kind);

        if (draw < 0 || draw > 99 || amount < 0 || amount > 0xffff_ffffL) {
            throw new IllegalArgumentException("Invalid reward card");
        }
    }
}
