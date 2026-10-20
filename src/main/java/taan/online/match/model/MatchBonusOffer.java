package taan.online.match.model;

import java.util.UUID;

public record MatchBonusOffer(
    UUID sessionId,
    UUID admissionId,
    long turnRevision,
    int pickerScalar,
    boolean consumed
) {
    public MatchBonusOffer used() {
        return new MatchBonusOffer(sessionId, admissionId, turnRevision, pickerScalar, true);
    }
}
