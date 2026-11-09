package taan.online.match.service.model;

import java.util.Optional;

public record MatchBonusDelivery(
    int slotIndex,
    int tick,
    int scalar10,
    int itemId,
    int opaque24,
    Optional<ScenarioTeamGaugeClaim> gauge,
    long entityId
) {
    public MatchBonusDelivery(
        int slotIndex,
        int tick,
        int scalar10,
        int itemId,
        int opaque24,
        Optional<ScenarioTeamGaugeClaim> gauge
    ) {
        this(slotIndex, tick, scalar10, itemId, opaque24, gauge, -1L);
    }

    public MatchBonusDelivery(int slotIndex, int tick, int scalar10, int itemId, int opaque24) {
        this(slotIndex, tick, scalar10, itemId, opaque24, Optional.empty(), -1L);
    }
}
