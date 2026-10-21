package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import taan.online.ExperienceTestFixtures;
import taan.online.MatchBootstrapFixture;
import taan.online.TestLevels;
import taan.online.character.model.CharacterProgress;
import taan.online.character.model.CharacterProgressSnapshot;
import taan.online.room.model.RoomMode;

class MatchRewardModelsTest {
    @ParameterizedTest
    @CsvSource({"-1,0,0,0,0", "4294967296,4294967296,0,0,0", "1,0,0,0,0",
        "0,4294967296,4294967296,0,0", "0,0,-1,1,0", "0,0,1,-1,0", "0,0,0,1,-1", "0,1,0,0,0"})
    void rejectsInconsistentGold(long before, long after, long base, long medal, long card) {
        assertThrows(IllegalArgumentException.class, () -> new MatchGoldReward(before, after, base, medal, card));
    }

    @Test
    void validatesCardsAndDrawsAndRollsBackAnOverflowProposal() {
        assertThrows(NullPointerException.class, () -> new MatchRewardCard(0, null, 0));

        for (int draw : new int[]{-1, 100}) {
            assertThrows(IllegalArgumentException.class, () -> new MatchRewardCard(draw, MatchRewardCardKind.GOLD, 0));
            assertThrows(IllegalArgumentException.class, () -> new MatchParticipantStatistics(1, 0, 0, false, draw));
        }

        for (long amount : new long[]{-1, 0x100000000L}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new MatchRewardCard(
                    0,
                    MatchRewardCardKind.GOLD,
                    amount
                )
            );
        }

        var source = ExperienceTestFixtures.result();
        var member = source.participants().getFirst().withDraw(90);
        var rules = new MatchRewardRules(TestLevels.table());

        assertThrows(
            IllegalArgumentException.class,
            () -> rules.calculate(
                source,
                member,
                CharacterProgress.initial(),
                0xffff_ffffL
            )
        );
        assertThrows(
            NullPointerException.class,
            () -> rules.calculate(
                source,
                source.participants().getFirst(),
                CharacterProgress.initial(),
                0
            )
        );
        assertEquals(90, member.statistics().rewardDraw());
    }

    @Test
    void countsDamagingScenariosOnceAndIgnoresSelfAlliesAndDeadTargets() {
        try (var f = new MatchBootstrapFixture(4, RoomMode.ITEM)) {
            f.start();
            var snapshot = f.snapshot();
            var source = snapshot.participants().getFirst();
            var enemies = snapshot.participants().stream().filter(p -> p.team() != source.team()).toList();
            var ally = snapshot.participants().stream().filter(p -> p.team() == source.team()
                && !p.sessionId().equals(source.sessionId())).findFirst().orElseThrow();
            var draft = new ScenarioWorldDraft(snapshot.world(), snapshot.definitions(), snapshot.participants());
            draft.fired(source.sessionId());
            draft.damage(source.sessionId(), enemies.getFirst().sessionId(), 1);
            draft.damage(source.sessionId(), enemies.getFirst().sessionId(), 2);
            draft.damage(source.sessionId(), enemies.getLast().sessionId(), 3);
            draft.finishShot(source.sessionId());
            var world = draft.finish(
                new ScenarioCommitReceipt("shots", source.sessionId(), source.admissionId(), 1, 1),
                Set.of(),
                1000
            );

            assertEquals(new MatchShotStatistics(1, 1), world.combatStatistics().shots(source.sessionId()));
            assertEquals(0, snapshot.world().combatStatistics().shots(source.sessionId()).damagingShots());

            var miss = new ScenarioWorldDraft(world, snapshot.definitions(), snapshot.participants());
            miss.fired(source.sessionId());
            miss.damage(source.sessionId(), source.sessionId(), 1);
            miss.damage(source.sessionId(), ally.sessionId(), 1);
            miss.dead(enemies.getFirst().sessionId());
            miss.damage(source.sessionId(), enemies.getFirst().sessionId(), 9000);
            miss.finishShot(source.sessionId());
            var next = miss.finish(
                new ScenarioCommitReceipt("miss", source.sessionId(), source.admissionId(), 2, 2),
                Set.of(),
                1000
            );

            assertEquals(new MatchShotStatistics(2, 1), next.combatStatistics().shots(source.sessionId()));
            assertEquals(0.5f, next.combatStatistics().shots(source.sessionId()).accuracy());
            assertEquals(1, world.combatStatistics().shots(source.sessionId()).acceptedShots());
        }
    }

    @Test
    void opensAllEightCardsWithoutDuplicatingAChoiceAndRejectsInvalidParticipants() {
        assertThrows(IllegalArgumentException.class, () -> new MatchCardSelection(Set.of(-1)));
        assertThrows(IllegalArgumentException.class, () -> new MatchCardSelection(Set.of(8)));
        var selection = new MatchCardSelection(Set.of(0, 1, 2, 3, 4, 5, 6, 7));

        assertEquals(MatchCardSelectionOutcome.IGNORED, selection.select(8, null, bound -> 0).outcome());

        for (int slot = 0; slot < 8; slot++) {
            assertEquals(slot, selection.select(slot, null, bound -> 0).cardIndex());
        }

        assertTrue(selection.select(7, null, bound -> 0).replay());
        assertEquals(7, selection.select(7, 0, bound -> 0).cardIndex());
    }

    @Test
    void treatsGoldAtTheExperienceCapAsAnAwardEvenWithoutMedals() {
        var source = ExperienceTestFixtures.result();
        var original = source.participants().getFirst();
        var member = new MatchParticipantResult(
            original.characterId(),
            0,
            original.team(),
            0,
            true,
            false,
            0,
            false,
            new MatchParticipantStatistics(1000, 1, 6000, false, 99),
            null
        );
        var awarded = new MatchRewardRules(TestLevels.table()).calculate(
            source,
            member,
            new CharacterProgress(1048576, 0, 0, 0),
            0
        );
        var result = source.rewarded(List.of(awarded));

        assertEquals(0, awarded.experienceReward().delta());
        assertEquals(35, awarded.goldReward().delta());
        assertEquals(MatchRewardCardKind.GOLD, awarded.card().kind());
        assertTrue(result.outcome().awardsGranted());
    }

    @Test
    void doesNotCreateRewardsForACancellationAndRejectsNegativeExperienceCardComponents() {
        var source = ExperienceTestFixtures.result();
        var original = source.participants().getFirst();
        var member = new MatchParticipantResult(
            original.characterId(),
            0,
            original.team(),
            0,
            true,
            false,
            0,
            false,
            new MatchParticipantStatistics(1000, 1, 6000, false),
            null
        );
        var result = new MatchResult(
            source.matchId(),
            source.finishedAt(),
            Optional.empty(),
            MatchOutcomeCause.TECHNICAL_CANCELLATION,
            "pvp-rewards-1.1.0",
            List.of(member),
            60000L
        );
        var awarded = new MatchRewardRules(TestLevels.table()).calculate(
            result,
            member,
            CharacterProgress.initial(),
            0
        );

        assertEquals(0, awarded.goldReward().delta());
        assertFalse(result.rewarded(List.of(awarded)).outcome().awardsGranted());
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchExperienceReward(
                0,
                1,
                new CharacterProgressSnapshot(member.characterId(), 1, CharacterProgress.initial()),
                1,
                0,
                0,
                0,
                -1
            )
        );
    }

    @Test
    void keepsShotCountersImmutableAndRejectsCorruptCounts() {
        var id = UUID.randomUUID();
        var statistics = new MatchCombatStatistics(Map.of(), Map.of(), Map.of(id, 3L), Map.of(id, 1L));

        assertEquals(new MatchShotStatistics(3, 1), statistics.shots(id));
        assertEquals(0, statistics.shots(UUID.randomUUID()).accuracy());
        assertEquals(2, statistics.fired(id).hit(id).credit(id, 1).killed(id).shots(id).damagingShots());
        assertThrows(UnsupportedOperationException.class, () -> statistics.damagingShots().clear());
        assertThrows(IllegalArgumentException.class, () -> new MatchShotStatistics(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchShotStatistics(1, -1));
        assertThrows(IllegalArgumentException.class, () -> new MatchShotStatistics(1, 2));
        assertThrows(IllegalArgumentException.class, () -> MatchCombatStatistics.empty().hit(id));
        assertThrows(
            ArithmeticException.class,
            () -> new MatchCombatStatistics(
                Map.of(),
                Map.of(),
                Map.of(id, Long.MAX_VALUE),
                Map.of(id, Long.MAX_VALUE)
            ).hit(id)
        );
        assertTrue(statistics.shots(id).accuracy() > 0);
    }
}
