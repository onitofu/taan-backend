package taan.online.match.service;

import java.util.Objects;
import java.util.UUID;

record MatchSubscription(UUID sessionId, MatchOutput output) {
    MatchSubscription {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(output);
    }
}
