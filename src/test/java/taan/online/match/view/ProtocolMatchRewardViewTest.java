package taan.online.match.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.ExperienceTestFixtures;
import taan.online.MatchBootstrapFixture;
import taan.online.TestLevels;
import taan.online.character.model.CharacterProgress;
import taan.online.character.protocol.codec.UpdateExpCodec;
import taan.online.character.protocol.codec.UpdateMoneyCodec;
import taan.online.match.model.MatchCombatStatistics;
import taan.online.match.model.MatchOutcome;
import taan.online.match.model.MatchResult;
import taan.online.match.model.MatchRewardRules;
import taan.online.match.protocol.codec.EndGameCodec;
import taan.online.match.protocol.codec.ResultSelectionCodec;
import taan.online.match.protocol.codec.SetRewardCodec;
import taan.online.match.protocol.packet.GameRewardExp;
import taan.online.match.protocol.packet.GameRewardGold;
import taan.online.match.service.model.MatchEndResult;
import taan.online.match.service.model.MatchRewardSelectionNotice;
import taan.online.room.model.RoomMode;

class ProtocolMatchRewardViewTest {
    @Test
    void sendsAbsoluteBalancesBeforePrepaidCardsAndOneEndGameWithAccuracyAndBothBonuses() {
        for (int draw : new int[]{0, 99}) {
            try (var f = new MatchBootstrapFixture(2, RoomMode.ITEM)) {
                f.start();
                var snapshot = f.snapshot();
                var shooter = snapshot.participants().getFirst();
                var original = MatchResult.finish(
                    snapshot.id(),
                    Instant.EPOCH,
                    new MatchOutcome(Set.of(shooter.team()), false),
                    snapshot.participants(),
                    new MatchCombatStatistics(
                        Map.of(shooter.sessionId(), 1000L),
                        Map.of(shooter.sessionId(), 2),
                        Map.of(shooter.sessionId(), 2L),
                        Map.of(shooter.sessionId(), 1L)
                    ),
                    Map.of(),
                    ExperienceTestFixtures.participation(shooter.sessionId())
                );
                var rules = new MatchRewardRules(TestLevels.table());
                var stored = original.rewarded(original.participants().stream().map(p -> rules.calculate(
                    original,
                    p.withDraw(draw),
                    new CharacterProgress(900, 0, 0, 0),
                    123
                )).toList());
                var output = new CollectingPacketOutput();
                var view = new ProtocolMatchView(output, TestLevels.table());
                view.end(new MatchEndResult(stored, snapshot.participants(), shooter.sessionId()));
                var batch = output.batches.getFirst();
                var winner = stored.participants().getFirst();

                assertEquals(4, batch.size());
                assertEquals(
                    winner.experienceReward().after().progress().experience(),
                    new UpdateExpCodec().decode(batch.getFirst()).currentExp()
                );
                assertEquals(winner.goldReward().after(), new UpdateMoneyCodec().decode(batch.get(1)).balance());
                var rewards = new SetRewardCodec().decode(batch.get(2));
                var end = new EndGameCodec().decode(batch.get(3));
                var slot = end.result().slots().get(shooter.slotIndex());

                assertEquals(8, rewards.rewards().size());
                assertEquals(winner.card().amount(), rewards.rewards().get(shooter.slotIndex()).amount());
                assertEquals(draw == 0, rewards.rewards().get(shooter.slotIndex()) instanceof GameRewardGold);
                assertEquals(draw == 99, rewards.rewards().get(shooter.slotIndex()) instanceof GameRewardExp);
                assertEquals(winner.goldReward().delta(), slot.displayedGold());
                assertEquals(0.5f, slot.accuracy());
                assertEquals(100, slot.bonuses().getFirst().expBonus());
                assertEquals(3, slot.bonuses().getLast().goldBonus());
                assertEquals(60, slot.bonuses().getLast().expBonus());
                assertTrue(rewards.rewards().stream().allMatch(card -> card.label().isEmpty()));

                view.selectReward(new MatchRewardSelectionNotice(3, shooter.slotIndex(), false));
                view.selectReward(new MatchRewardSelectionNotice(3, shooter.slotIndex(), true));

                assertEquals(3, new ResultSelectionCodec().selected(output.batches.get(1).getFirst()).cardIndex());
                assertEquals(3, new ResultSelectionCodec().alreadyOpened(output.batches.get(2).getFirst()).cardIndex());
            }
        }
    }
}
