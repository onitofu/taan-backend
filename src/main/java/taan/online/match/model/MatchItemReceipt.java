package taan.online.match.model;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public record MatchItemReceipt(
    long grantSequence,
    long turnRevision,
    int inventoryIndex,
    int itemId,
    boolean confirmed,
    UUID admissionId,
    MatchItemSource source,
    OptionalLong boxEntityId,
    Optional<String> scenarioHash
) {
    public MatchItemReceipt acknowledged() {
        return new MatchItemReceipt(
            grantSequence,
            turnRevision,
            inventoryIndex,
            itemId,
            true,
            admissionId,
            source,
            boxEntityId,
            scenarioHash
        );
    }
}
