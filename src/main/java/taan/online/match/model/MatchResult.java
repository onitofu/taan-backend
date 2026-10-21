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
    List<MatchParticipantResult> participants,
    Long rewardDurationMillis
) {
    public MatchResult(
        MatchId matchId,
        Instant finishedAt,
        Optional<RoomTeam> winningTeam,
        MatchOutcomeCause cause,
        String policyVersion,
        List<MatchParticipantResult> participants
    ) {
        this(matchId, finishedAt, winningTeam, cause, policyVersion, participants, null);
    }

    public MatchResult {
        Objects.requireNonNull(matchId);
        finishedAt = Objects.requireNonNull(finishedAt).truncatedTo(ChronoUnit.MICROS);
        Objects.requireNonNull(winningTeam);
        Objects.requireNonNull(cause);
        Objects.requireNonNull(policyVersion);
        participants = List.copyOf(participants);

        if (rewardDurationMillis != null && (rewardDurationMillis < 0 || rewardDurationMillis > 1800000)
            || winningTeam.isEmpty() != (cause == MatchOutcomeCause.TECHNICAL_CANCELLATION)
            || policyVersion.isBlank() || policyVersion.length() > 32 || participants.size() > 8
            || participants.stream().map(MatchParticipantResult::characterId).distinct().count() != participants.size()
            || participants.stream().map(MatchParticipantResult::slotIndex).distinct().count() != participants.size()
            || winningTeam.isEmpty() && participants.stream().anyMatch(p -> p.forceMedal() || p.killMedal())) {
            throw new IllegalArgumentException("Inconsistent match result");
        }
    }

    public static MatchResult finish(
        MatchId id,
        Instant finishedAt,
        MatchOutcome outcome,
        List<MatchParticipant> roster,
        MatchCombatStatistics statistics,
        Map<UUID, MatchExitKind> exits
    ) {
        return finish(id, finishedAt, outcome, roster, statistics, exits, MatchParticipationState.initial());
    }

    public static MatchResult finish(
        MatchId id,
        Instant finishedAt,
        MatchOutcome outcome,
        List<MatchParticipant> roster,
        MatchCombatStatistics statistics,
        Map<UUID, MatchExitKind> exits,
        MatchParticipationState participation
    ) {
        boolean completed = !outcome.winningTeams().isEmpty() && participation.durationMillis() > 0;
        var drafts = roster.stream().map(member -> {
            var session = member.sessionId();
            return new MatchParticipantResult(
                member.character().id(),
                member.slotIndex(),
                member.team(),
                statistics.damage(session),
                completed && !exits.containsKey(session),
                false,
                statistics.kills(session),
                false,
                new MatchParticipantStatistics(
                    member.maxHp(),
                    statistics.acceptedShots(session),
                    participation.longestTurnMillis().getOrDefault(session, 0L),
                    participation.voluntarilyPassed().contains(session)
                ),
                null,
                null,
                null,
                statistics.shots(session)
            );
        }).toList();
        long maximum = drafts.stream().filter(p -> p.awardEligible() && !p.passive())
            .mapToLong(MatchParticipantResult::damage).max().orElse(0);
        var results = drafts.stream().map(p -> new MatchParticipantResult(
            p.characterId(),
            p.slotIndex(),
            p.team(),
            p.damage(),
            p.awardEligible(),
            p.awardEligible() && !p.passive() && maximum > 0 && p.damage() == maximum,
            p.kills(),
            p.awardEligible() && !p.passive() && p.kills() >= 2,
            p.statistics(),
            null,
            null,
            null,
            p.shots()
        )).toList();
        return new MatchResult(
            id,
            finishedAt,
            outcome.winningTeams().stream().findFirst(),
            outcome.cause(),
            "pvp-rewards-1.1.0",
            results,
            participation.durationMillis()
        );
    }

    public MatchResult rewarded(List<MatchParticipantResult> rewards) {
        return new MatchResult(matchId, finishedAt, winningTeam, cause, policyVersion, rewards, rewardDurationMillis);
    }

    public MatchOutcome outcome() {
        return new MatchOutcome(
            winningTeam.map(Set::of).orElseGet(Set::of),
            participants.stream().anyMatch(
                p -> p.forceMedal() || p.killMedal()
                    || p.experienceReward() != null && p.experienceReward().delta() > 0
                    || p.goldReward() != null && p.goldReward().delta() > 0
            ),
            cause
        );
    }
}
