package taan.online.match.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterProgress;
import taan.online.character.model.CharacterProgressSnapshot;
import taan.online.match.model.MatchExperienceReward;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchOutcomeCause;
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchResult;
import taan.online.room.model.RoomTeam;

final class MatchResultFixtures {
    private MatchResultFixtures() {
    }

    static MatchResult historicalExperience(long first, long second) {
        var proposal = MatchExperienceJdbcTest.result(first, second);
        var participants = proposal.participants().stream().map(p -> {
            boolean won = p.team() == RoomTeam.BLUE;
            return p.rewarded(new MatchExperienceReward(
                900,
                1,
                new CharacterProgressSnapshot(p.characterId(), 2, new CharacterProgress(won ? 1016 : 952, 1, 1, 0)),
                won ? 100 : 45,
                won ? 10 : 5,
                won ? 6 : 2,
                0
            ));
        }).toList();
        return new MatchResult(
            proposal.matchId(),
            proposal.finishedAt(),
            proposal.winningTeam(),
            proposal.cause(),
            "pvp-exp-1.0.2",
            participants,
            60000L
        );
    }

    static MatchResult result(long first, long second) {
        return new MatchResult(
            new MatchId(UUID.randomUUID()),
            Instant.parse("2026-10-21T00:00:00Z"),
            Optional.of(RoomTeam.BLUE),
            MatchOutcomeCause.ELIMINATION,
            "damage-medal-v1",
            List.of(
                new MatchParticipantResult(new CharacterId(first), 0, RoomTeam.BLUE, 400, true, true),
                new MatchParticipantResult(new CharacterId(second), 2, RoomTeam.RED, 400, true, true)
            )
        );
    }
}
