package taan.online.match.model;

import java.util.UUID;

public record PowerQuizOpportunity(
    UUID sessionId,
    UUID admissionId,
    int slotIndex,
    int sourceSlot,
    long turnRevision,
    String scenarioHash,
    int pickerScalar,
    boolean consumed
) {
    public PowerQuizOpportunity used() {
        return new PowerQuizOpportunity(
            sessionId,
            admissionId,
            slotIndex,
            sourceSlot,
            turnRevision,
            scenarioHash,
            pickerScalar,
            true
        );
    }
}
