package taan.online.match.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import taan.online.CollectingPacketOutput;
import taan.online.ExperienceTestFixtures;
import taan.online.MatchBootstrapFixture;
import taan.online.TestLevels;
import taan.online.character.model.CharacterLevelTable;
import taan.online.character.protocol.codec.UpdateExpCodec;
import taan.online.match.model.MatchOutcomeCause;
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchParticipantStatistics;
import taan.online.match.model.MatchResult;
import taan.online.match.protocol.codec.EndGameCodec;
import taan.online.match.protocol.codec.SetRewardCodec;
import taan.online.match.protocol.packet.ResultBonus;
import taan.online.match.service.model.MatchEndResult;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomTeam;

class ProtocolMatchExperienceViewTest {
    @ParameterizedTest
    @ValueSource(longs = {900, CharacterLevelTable.EXPERIENCE_CAP})
    void deliversCommittedComponentsAndKeepsMedalsVisibleWhenBothBonusAmountsAreZero(long before) {
        try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
            f.start();
            var snapshot = f.snapshot();
            var members = snapshot.participants().stream().map(p -> {
                boolean winner = p.team() == RoomTeam.BLUE;
                return new MatchParticipantResult(
                    p.character().id(),
                    p.slotIndex(),
                    p.team(),
                    winner ? 1000 : 200,
                    true,
                    winner,
                    winner ? 2 : 0,
                    winner,
                    new MatchParticipantStatistics(1000, 1, 6000, false),
                    null
                );
            }).toList();
            var result = ExperienceTestFixtures.settle(
                new MatchResult(
                    snapshot.id(),
                    Instant.EPOCH,
                    Optional.of(RoomTeam.BLUE),
                    MatchOutcomeCause.ELIMINATION,
                    "pvp-exp-1.1.0",
                    members,
                    60000L
                ),
                before
            );

            boolean capped = before == CharacterLevelTable.EXPERIENCE_CAP;

            for (var recipient : snapshot.participants()) {
                var output = new CollectingPacketOutput();
                new ProtocolMatchView(output, TestLevels.table()).end(new MatchEndResult(
                    result,
                    snapshot.participants(),
                    recipient.sessionId()
                ));
                var batch = output.batches.getFirst();
                var update = new UpdateExpCodec().decode(batch.getFirst());
                var cards = new SetRewardCodec().decode(batch.get(1));
                var end = new EndGameCodec().decode(batch.getLast());

                assertEquals(3, batch.size());
                assertEquals(0, update.localIndex());
                assertEquals(
                    capped ? CharacterLevelTable.EXPERIENCE_CAP : recipient.team() == RoomTeam.BLUE ? 2060 : 1054,
                    update.currentExp()
                );
                assertTrue(update.nextLevelExp() > update.levelStartExp());
                assertTrue(cards.rewards().stream().allMatch(card -> card.amount() == 0));
                var winner = end.result().slots().stream().filter(slot -> slot.kills() == 2).findFirst().orElseThrow();
                assertEquals(capped ? 109 : 2, winner.level());
                assertEquals(capped ? 0 : 1160, winner.expDeltaFallback());
                assertEquals(List.of(1, 1, 0), winner.medals());
                assertEquals(before, winner.expSnapshots().getFirst().currentExp());
                assertEquals(
                    capped ? CharacterLevelTable.EXPERIENCE_CAP : 2060,
                    winner.expSnapshots().getLast().currentExp()
                );
                assertEquals(
                    capped ? List.of() : List.of(new ResultBonus(0, 100, 0), new ResultBonus(4, 60, 0)),
                    winner.bonuses()
                );
                assertEquals(0, winner.displayedGold());

                var loser = end.result().slots().stream().filter(slot -> slot.damage() == 200)
                    .findFirst().orElseThrow();
                assertEquals(List.of(0, 0, 0), loser.medals());
                assertEquals(capped ? List.of() : List.of(new ResultBonus(0, 14, 0)), loser.bonuses());
            }
        }
    }
}
