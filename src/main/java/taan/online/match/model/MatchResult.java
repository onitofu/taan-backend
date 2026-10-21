package taan.online.match.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import taan.online.room.model.RoomTeam;

public record MatchResult(
    MatchId matchId,
    Instant finishedAt,
    Optional<RoomTeam> winningTeam,
    MatchOutcomeCause cause,
    String policyVersion,
    List<MatchParticipantResult> participants
) {
    public MatchResult {
        Objects.requireNonNull(matchId);
        finishedAt = Objects.requireNonNull(finishedAt).truncatedTo(ChronoUnit.MICROS);
        Objects.requireNonNull(winningTeam);
        Objects.requireNonNull(cause);
        Objects.requireNonNull(policyVersion);
        participants = List.copyOf(participants);

        if (winningTeam.isEmpty() != (cause == MatchOutcomeCause.TECHNICAL_CANCELLATION)
            || policyVersion.isBlank() || policyVersion.length() > 32 || participants.size() > 8
            || participants.stream().map(MatchParticipantResult::characterId).distinct().count() != participants.size()
            || participants.stream().map(MatchParticipantResult::slotIndex).distinct().count() != participants.size()
            || winningTeam.isEmpty() && participants.stream().anyMatch(MatchParticipantResult::forceMedal)) {
            throw new IllegalArgumentException("Inconsistent match result");
        }
    }

    public static MatchResult finish(
        MatchId id,
        Instant finishedAt,
        MatchOutcome outcome,
        List<MatchParticipant> roster,
        MatchDamageStatistics statistics,
        Map<UUID, MatchExitKind> exits
    ) {
        long maximum = roster.stream().filter(member -> !exits.containsKey(member.sessionId()))
            .mapToLong(member -> statistics.damage(member.sessionId())).max().orElse(0);
        boolean completed = !outcome.winningTeams().isEmpty();
        var results = roster.stream().map(
            member -> {
                long damage = statistics.damage(member.sessionId());
                boolean eligible = completed && !exits.containsKey(member.sessionId());
                return new MatchParticipantResult(
                    member.character().id(),
                    member.slotIndex(),
                    member.team(),
                    damage,
                    eligible,
                    eligible && maximum > 0 && damage == maximum
                );
            }
        ).toList();
        return new MatchResult(
            id,
            finishedAt,
            outcome.winningTeams().stream().findFirst(),
            outcome.cause(),
            "damage-medal-v1",
            results
        );
    }

    public MatchOutcome outcome() {
        return new MatchOutcome(
            winningTeam.map(Set::of).orElseGet(Set::of),
            participants.stream().anyMatch(MatchParticipantResult::forceMedal),
            cause
        );
    }
}
