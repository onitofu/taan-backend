package taan.online.match.model;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

public record MatchTurn(
    int activeSlot,
    int windRaw,
    int energyGrant,
    int boxAttemptBudget,
    int pickerBase,
    List<Integer> order,
    long revision,
    OptionalLong issuedAtNanos
) {
    public MatchTurn {
        order = List.copyOf(order);
        Objects.requireNonNull(issuedAtNanos);
    }
}
