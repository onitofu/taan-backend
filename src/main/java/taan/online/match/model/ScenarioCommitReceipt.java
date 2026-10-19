package taan.online.match.model;

import java.util.UUID;

public record ScenarioCommitReceipt(
    String fingerprint,
    UUID sessionId,
    UUID admissionId,
    long turnRevision,
    long worldRevision
) {
}
