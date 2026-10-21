package taan.online.match.service.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchResult;

public record MatchEndResult(MatchResult result, List<MatchParticipant> participants, UUID recipientSessionId) {
    public MatchEndResult(MatchResult result, List<MatchParticipant> participants) {
        this(result, participants, null);
    }

    public MatchEndResult {
        Objects.requireNonNull(result);
        participants = List.copyOf(participants);
    }
}
