package taan.online;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterProgress;
import taan.online.match.model.MatchExperienceRules;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchOutcomeCause;
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchParticipantStatistics;
import taan.online.match.model.MatchParticipationState;
import taan.online.match.model.MatchResult;
import taan.online.room.model.RoomTeam;

public final class ExperienceTestFixtures {
    private ExperienceTestFixtures() {
    }

    public static MatchParticipationState participation(UUID sessionId) {
        return MatchParticipationState.initial().start(sessionId, 0).stop(60000000000L);
    }

    public static MatchResult result() {
        return new MatchResult(
            new MatchId(UUID.randomUUID()),
            Instant.EPOCH,
            Optional.of(RoomTeam.BLUE),
            MatchOutcomeCause.ELIMINATION,
            "pvp-exp-1.1.0",
            List.of(
                new MatchParticipantResult(
                    new CharacterId(1),
                    0,
                    RoomTeam.BLUE,
                    1000,
                    true,
                    true,
                    2,
                    true,
                    new MatchParticipantStatistics(1000, 1, 6000, false),
                    null
                ),
                new MatchParticipantResult(
                    new CharacterId(2),
                    2,
                    RoomTeam.RED,
                    200,
                    true,
                    false,
                    0,
                    false,
                    new MatchParticipantStatistics(1000, 1, 6000, false),
                    null
                )
            ),
            60000L
        );
    }

    public static MatchResult settle(MatchResult result, long before) {
        var rules = new MatchExperienceRules(TestLevels.table());
        return result.rewarded(result.participants().stream().map(p -> p.rewarded(
            rules.calculate(result, p, new CharacterProgress(before, 0, 0, 0))
        )).toList());
    }
}
