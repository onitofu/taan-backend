package taan.online.match.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record PowerQuizOpportunity(
    UUID sessionId,
    UUID admissionId,
    int slotIndex,
    int sourceSlot,
    long turnRevision,
    String scenarioHash,
    int pickerScalar,
    boolean consumed,
    Set<Integer> knownGaugeValues
) {
    public PowerQuizOpportunity(
        UUID sessionId,
        UUID admissionId,
        int slotIndex,
        int sourceSlot,
        long turnRevision,
        String scenarioHash,
        int pickerScalar,
        boolean consumed
    ) {
        this(
            sessionId,
            admissionId,
            slotIndex,
            sourceSlot,
            turnRevision,
            scenarioHash,
            pickerScalar,
            consumed,
            Set.of()
        );
    }

    public PowerQuizOpportunity {
        knownGaugeValues = Set.copyOf(knownGaugeValues);
    }

    public PowerQuizOpportunity observed(int value) {
        var known = new HashSet<>(knownGaugeValues);
        known.add(value);
        return new PowerQuizOpportunity(
            sessionId,
            admissionId,
            slotIndex,
            sourceSlot,
            turnRevision,
            scenarioHash,
            pickerScalar,
            consumed,
            known
        );
    }

    public PowerQuizOpportunity used() {
        return new PowerQuizOpportunity(
            sessionId,
            admissionId,
            slotIndex,
            sourceSlot,
            turnRevision,
            scenarioHash,
            pickerScalar,
            true,
            knownGaugeValues
        );
    }
}
