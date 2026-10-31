package taan.online.match.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import taan.online.CollectingPacketOutput;
import taan.online.ExperienceTestFixtures;
import taan.online.MatchBootstrapFixture;
import taan.online.TestLevels;
import taan.online.character.model.CharacterLevelTable;
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
import taan.online.match.protocol.packet.ResultBonus;
import taan.online.match.service.model.MatchEndResult;
import taan.online.match.service.model.MatchRewardSelectionNotice;
import taan.online.room.model.RoomMode;

class ProtocolMatchRewardViewTest {
    @ParameterizedTest
    @CsvSource({
        "0, 900",
        "99, 900",
        "0, 1048576",
        "99, 1048576"
    })
    void sendsCommittedBalancesCardsAndOnlyNonzeroBonusesIncludingGoldAtTheExperienceCap(int draw, long before) {
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
                new CharacterProgress(before, 4, 5, 6),
                123
            )).toList());
            var output = new CollectingPacketOutput();
            var view = new ProtocolMatchView(output, TestLevels.table());
            view.end(new MatchEndResult(stored, snapshot.participants(), shooter.sessionId()));
            var batch = output.batches.getFirst();
            var winner = stored.participants().getFirst();
            boolean capped = before == CharacterLevelTable.EXPERIENCE_CAP;
            boolean experienceCard = draw == 99 && !capped;

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
            assertEquals(!experienceCard, rewards.rewards().get(shooter.slotIndex()) instanceof GameRewardGold);
            assertEquals(experienceCard, rewards.rewards().get(shooter.slotIndex()) instanceof GameRewardExp);
            assertEquals(winner.goldReward().delta(), slot.displayedGold());
            assertEquals(0.5f, slot.accuracy());
            assertEquals(
                capped ? List.of(new ResultBonus(4, 0, 3))
                    : List.of(new ResultBonus(0, 100, 0), new ResultBonus(4, 60, 3)),
                slot.bonuses()
            );
            assertTrue(rewards.rewards().stream().allMatch(card -> card.label().isEmpty()));

            var other = stored.participants().getLast();
            var otherSlot = end.result().slots().get(other.slotIndex());
            assertEquals(List.of(0, 0, 0), otherSlot.medals());
            assertEquals(4, other.experienceReward().after().progress().forceMedals());
            assertEquals(5, other.experienceReward().after().progress().killMedals());
            assertFalse(otherSlot.bonuses().stream().anyMatch(bonus -> bonus.kind() == 4));
            assertEquals(capped ? 0 : 1, otherSlot.bonuses().size());

            view.selectReward(new MatchRewardSelectionNotice(3, shooter.slotIndex(), false));
            view.selectReward(new MatchRewardSelectionNotice(3, shooter.slotIndex(), true));

            assertEquals(3, new ResultSelectionCodec().selected(output.batches.get(1).getFirst()).cardIndex());
            assertEquals(3, new ResultSelectionCodec().alreadyOpened(output.batches.get(2).getFirst()).cardIndex());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0, 0, false",
        "0, 10, 0, true",
        "0, 0, 20, true",
        "0, 10, 20, true",
        "1, 0, 0, false",
        "1, 10, 0, true",
        "1, 0, 20, true",
        "1, 10, 20, true",
        "2, 0, 0, false",
        "2, 10, 0, true",
        "2, 0, 20, true",
        "2, 10, 20, true",
        "3, 0, 0, false",
        "3, 10, 0, true",
        "3, 0, 20, true",
        "3, 10, 20, true",
        "4, 0, 0, false",
        "4, 10, 0, true",
        "4, 0, 20, true",
        "4, 10, 20, true"
    })
    void appliesTheSameZeroSuppressionToEveryBonusCategory(int kind, int experience, int gold, boolean visible) {
        var bonus = new ResultBonus(kind, experience, gold);

        var actual = ProtocolMatchView.activeBonuses(List.of(bonus));

        assertEquals(visible ? List.of(bonus) : List.of(), actual);
    }

    @Test
    void preservesTheOrderAndAmountsOfActiveCategoriesInAnImmutableIndependentList() {
        var friendship = new ResultBonus(2, 0, 17);
        var accessory = new ResultBonus(1, 25, 0);
        var event = new ResultBonus(3, 12, 9);
        var candidates = new ArrayList<>(
            List.of(
                new ResultBonus(4, 0, 0),
                friendship,
                new ResultBonus(0, 0, 0),
                accessory,
                event
            )
        );

        var actual = ProtocolMatchView.activeBonuses(candidates);

        assertEquals(List.of(friendship, accessory, event), actual);
        assertEquals(5, candidates.size());
        candidates.clear();
        assertEquals(List.of(friendship, accessory, event), actual);
        assertThrows(UnsupportedOperationException.class, actual::clear);
    }
}
