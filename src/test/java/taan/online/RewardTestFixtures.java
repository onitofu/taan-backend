package taan.online;

import java.util.List;
import taan.online.character.model.CharacterId;
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchParticipantStatistics;
import taan.online.match.model.MatchResult;
import taan.online.match.model.MatchShotStatistics;

public final class RewardTestFixtures {
    private RewardTestFixtures() {
    }

    public static MatchResult result(long winner, long loser, int draw) {
        var original = ExperienceTestFixtures.result();
        var first = original.participants().getFirst();
        var second = original.participants().getLast();
        return new MatchResult(
            original.matchId(),
            original.finishedAt(),
            original.winningTeam(),
            original.cause(),
            "pvp-rewards-1.1.0",
            List.of(
                new MatchParticipantResult(
                    new CharacterId(winner),
                    0,
                    first.team(),
                    first.damage(),
                    true,
                    true,
                    2,
                    true,
                    new MatchParticipantStatistics(
                        1000,
                        2,
                        6000,
                        false,
                        draw
                    ),
                    null,
                    null,
                    null,
                    new MatchShotStatistics(
                        2,
                        1
                    )
                ),
                new MatchParticipantResult(
                    new CharacterId(loser),
                    2,
                    second.team(),
                    second.damage(),
                    true,
                    false,
                    0,
                    false,
                    new MatchParticipantStatistics(
                        1000,
                        1,
                        6000,
                        false,
                        draw
                    ),
                    null,
                    null,
                    null,
                    new MatchShotStatistics(
                        1,
                        0
                    )
                )
            ),
            60000L
        );
    }
}
