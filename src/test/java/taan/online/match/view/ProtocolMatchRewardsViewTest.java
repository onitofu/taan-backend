package taan.online.match.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.ExperienceTestFixtures;
import taan.online.MatchBootstrapFixture;
import taan.online.TestLevels;
import taan.online.match.model.MatchCombatStatistics;
import taan.online.match.model.MatchOutcome;
import taan.online.match.model.MatchResult;
import taan.online.match.protocol.codec.EndGameCodec;
import taan.online.match.protocol.codec.SetRewardCodec;
import taan.online.match.service.model.MatchEndResult;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomTeam;

class ProtocolMatchRewardsViewTest {
    @Test
    void showsDamageAndOneForceMedalForBothTiedLeadersWithoutPromisingExperienceOrMoney() {
        try (var fixture = new MatchBootstrapFixture(4, RoomMode.ITEM)) {
            fixture.start();
            var snapshot = fixture.snapshot();
            var blue = snapshot.participants().stream().filter(p -> p.team() == RoomTeam.BLUE)
                .findFirst().orElseThrow();
            var red = snapshot.participants().stream().filter(p -> p.team() == RoomTeam.RED).findFirst().orElseThrow();
            var result = MatchResult.finish(
                snapshot.id(),
                Instant.EPOCH,
                new MatchOutcome(Set.of(RoomTeam.BLUE), false),
                snapshot.participants(),
                new MatchCombatStatistics(Map.of(blue.sessionId(), 400L, red.sessionId(), 400L)),
                Map.of(),
                ExperienceTestFixtures.participation(snapshot.participants().getFirst().sessionId())
            );
            var output = new CollectingPacketOutput();
            new ProtocolMatchView(output, TestLevels.table()).end(new MatchEndResult(result, snapshot.participants()));
            var batch = output.batches.getFirst();
            var cards = new SetRewardCodec().decode(batch.getFirst());
            var end = new EndGameCodec().decode(batch.getLast());

            assertEquals(2, batch.size());
            assertEquals(1, end.result().resultKind());

            for (var participant : snapshot.participants()) {
                int index = participant.slotIndex();
                boolean leader = participant.sessionId().equals(blue.sessionId())
                    || participant.sessionId().equals(red.sessionId());
                var slot = end.result().slots().get(index);

                assertEquals(participant.character().level(), slot.level());
                assertEquals(leader ? 400 : 0, slot.damage());
                assertEquals(leader ? 1 : 0, slot.medals().getFirst());
                assertEquals(0, slot.expDeltaFallback());
                assertEquals(0, slot.displayedGold());
                assertEquals(participant.character().id().value(), cards.rewards().get(index).characterId());
                assertEquals(0, cards.rewards().get(index).amount());
            }
        }
    }
}
