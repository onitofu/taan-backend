package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.TestLevels;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterProgress;
import taan.online.room.model.RoomTeam;

class MatchRewardPolicyTest {
    @Test
    void preservesLegacyMedalOnlyBalancesAndDoesNotOverflowAnUnchangedCounter() {
        var policies = new MatchRewardPolicies(TestLevels.table());

        for (boolean awarded : new boolean[]{false, true}) {
            var participant = new MatchParticipantResult(new CharacterId(1), 0, RoomTeam.BLUE, 100, true, awarded);
            var result = new MatchResult(
                new MatchId(UUID.randomUUID()),
                Instant.EPOCH,
                Optional.of(RoomTeam.BLUE),
                MatchOutcomeCause.ELIMINATION,
                "damage-medal-v1",
                List.of(participant)
            );
            var policy = policies.select(result);
            assertInstanceOf(LegacyMatchRewardPolicy.class, policy);
            var before = new CharacterProgress(900, awarded ? 4 : 0xffffffffL, 3, 2);
            var calculation = policy.calculate(result, participant, before, 44, 123);

            assertSame(participant, calculation.participant());
            assertEquals(123, calculation.gold());
            assertEquals(44, calculation.level());
            assertEquals(new CharacterProgress(900, awarded ? 5 : 0xffffffffL, 3, 2), calculation.progress());

            if (awarded) {
                assertThrows(MatchMedalLimitException.class, () -> policy.calculate(
                    result,
                    participant,
                    new CharacterProgress(900, 0xffffffffL, 3, 2),
                    44,
                    123
                ));
            }
        }
    }
}
