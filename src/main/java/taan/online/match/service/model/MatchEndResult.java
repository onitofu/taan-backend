package taan.online.match.service.model;

import java.util.List;
import java.util.Objects;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchResult;

public record MatchEndResult(MatchResult result, List<MatchParticipant> participants) {
    public MatchEndResult {
        Objects.requireNonNull(result);
        participants = List.copyOf(participants);
    }
}
