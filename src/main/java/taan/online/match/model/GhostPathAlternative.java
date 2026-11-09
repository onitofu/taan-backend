package taan.online.match.model;

import java.util.List;

public record GhostPathAlternative(List<GhostPickupClaim> pickups) {
    public GhostPathAlternative {
        pickups = List.copyOf(pickups);
    }
}
