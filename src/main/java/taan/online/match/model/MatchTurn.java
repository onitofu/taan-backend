package taan.online.match.model;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

public record MatchTurn(
    int activeSlot,
    int windRaw,
    int energyGrant,
    int boxAttemptBudget,
    int pickerBase,
    List<Integer> order,
    long revision,
    OptionalLong issuedAtNanos,
    long round,
    Set<Integer> remainingThisRound,
    long randomState
) {
    public MatchTurn(
        int activeSlot,
        int windRaw,
        int energyGrant,
        int boxAttemptBudget,
        int pickerBase,
        List<Integer> order,
        long revision,
        OptionalLong issuedAtNanos
    ) {
        this(
            activeSlot,
            windRaw,
            energyGrant,
            boxAttemptBudget,
            pickerBase,
            order,
            revision,
            issuedAtNanos,
            1,
            Set.copyOf(order),
            0
        );
    }

    public MatchTurn issuedAt(long now) {
        return new MatchTurn(
            activeSlot,
            windRaw,
            energyGrant,
            boxAttemptBudget,
            pickerBase,
            order,
            revision,
            OptionalLong.of(now),
            round,
            remainingThisRound,
            randomState
        );
    }

    public MatchTurn {
        order = List.copyOf(order);
        remainingThisRound = Set.copyOf(remainingThisRound);
        Objects.requireNonNull(issuedAtNanos);
    }
}
