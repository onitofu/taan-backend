package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import taan.online.match.model.MatchLootState;
import taan.online.match.model.PowerQuizOpportunity;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.ReportMatchBonusCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.ScenarioEventTime;
import taan.online.match.service.model.ScenarioTeamGaugeClaim;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomTeam;

class NoItemQuizTest {
    @Test
    void rebasesTwoSimultaneousAlliedWinsAndDoesNotLoseAPreCapEntitlement() throws Exception {
        for (var mode : List.of(RoomMode.NO_ITEM, RoomMode.CAPTAIN_NO_ITEM)) {
            try (var f = new NoItemFixture(mode, RoomRule.POWER_QUIZ, 4); var logs = new LogCapture()) {
                f.gauge(RoomTeam.RED, 499);
                assertFalse(f.report(f.quiz(2, 499)));
                assertEquals(ScenarioApplicationOutcome.APPLIED, f.apply(f.shot()).outcome());
                var before = f.snapshot();
                assertEquals(2, before.loot().quizOpportunities().size());
                var first = f.quiz(2, 499);
                var second = f.quiz(3, 499);

                assertTrue(f.report(first));
                assertTrue(f.report(second));

                var after = f.snapshot();
                assertEquals(509, after.world().teamGauges().get(RoomTeam.RED).committed());
                assertEquals(499, before.world().teamGauges().get(RoomTeam.RED).committed());
                assertTrue(after.loot().inventories().isEmpty());
                assertEquals(0, after.loot().grantSequence());
                assertEquals(2, f.f.owner.bonuses.size());
                assertEquals(499, f.f.owner.bonuses.getFirst().gauge().orElseThrow().oldGauge());
                assertEquals(504, f.f.owner.bonuses.getFirst().gauge().orElseThrow().newGauge());
                assertEquals(504, f.f.owner.bonuses.getLast().gauge().orElseThrow().oldGauge());
                assertEquals(509, f.f.owner.bonuses.getLast().gauge().orElseThrow().newGauge());
                assertEquals(f.f.owner.bonuses, f.f.guest.bonuses);
                assertFalse(f.report(first));
                assertFalse(f.report(second));
                assertSame(after, f.snapshot());
                assertTrue(logs.text().contains("gaugeBefore=\"504\""));
                f.acknowledge();
                assertTrue(f.snapshot().loot().quizOpportunities().isEmpty());
                assertEquals(509, f.snapshot().world().teamGauges().get(RoomTeam.RED).committed());
            }
        }
    }

    @Test
    void validatesTheWholeClaimAndNeverConsumesAnInvalidWin() throws Exception {
        try (var f = new NoItemFixture(RoomMode.NO_ITEM, RoomRule.POWER_QUIZ, 2)) {
            assertEquals(ScenarioApplicationOutcome.APPLIED, f.apply(f.shot()).outcome());
            var before = f.snapshot();

            for (var gauge : List.of(
                gauge(0, 12, 4, 0, 5),
                gauge(2, 11, 4, 0, 5),
                gauge(2, 12, 3, 0, 5),
                gauge(2, 12, 4, 0, -1),
                gauge(2, 12, 4, 0, 65536),
                gauge(2, 12, 4, 1, 6),
                gauge(2, 12, 4, 0, 6)
            )) {
                assertFalse(f.report(claim(f, Optional.of(gauge), 2, 0, -1L)));
                assertSame(before, f.snapshot());
            }

            assertFalse(f.report(new ReportMatchBonusCommand(
                f.context(2),
                0,
                0,
                Optional.of(gauge(0, 12, 4, 0, 5)),
                -1L,
                0,
                0,
                0,
                0
            )));
            assertFalse(f.report(claim(f, Optional.empty(), 2, 0, -1L)));
            assertFalse(f.report(claim(f, Optional.of(gauge(2, 12, 4, 0, 5)), 0, 0, -1L)));
            assertFalse(f.report(claim(f, Optional.of(gauge(2, 12, 4, 0, 5)), 2, 5, -1L)));
            assertFalse(f.report(claim(f, Optional.of(gauge(2, 12, 4, 0, 5)), 2, 0, 1L)));
            f.f.admitted = false;
            assertFalse(f.report(f.quiz(2, 0)));
            f.f.admitted = true;
            assertSame(before, f.snapshot());
            assertTrue(f.report(f.quiz(2, 0)));
            assertEquals(5, f.snapshot().world().teamGauges().get(RoomTeam.RED).committed());
        }

        try (var f = new NoItemFixture(RoomMode.NO_ITEM, RoomRule.POWER_QUIZ, 2, MatchLootPolicy.disabled())) {
            f.apply(f.shot());
            assertFalse(f.report(f.quiz(2, 0)));
            assertTrue(f.f.owner.bonuses.isEmpty());
        }
    }

    @Test
    void consumesACappedWinWithoutAGaugeOrItemAndRejectsNewGenerationAtLevelFour() throws Exception {
        try (var f = new NoItemFixture(RoomMode.NO_ITEM, RoomRule.POWER_QUIZ, 2)) {
            f.gauge(RoomTeam.RED, 400);
            assertEquals(ScenarioApplicationOutcome.APPLIED, f.apply(f.shot()).outcome());
            assertFalse(f.report(f.quiz(2, 400)));
            assertTrue(f.report(claim(f, Optional.empty(), 2, 0, -1L)));
            assertEquals(400, f.snapshot().world().teamGauges().get(RoomTeam.RED).committed());
            assertTrue(f.f.owner.bonuses.getFirst().gauge().isEmpty());
            assertEquals(0, f.f.owner.bonuses.getFirst().itemId());
            assertTrue(f.snapshot().loot().inventories().isEmpty());
            assertFalse(f.report(claim(f, Optional.empty(), 2, 0, -1L)));
        }
    }

    @Test
    void fencesAStaleOpportunityAndAnExpiredWindowAndSkipsADepartedAlly() throws Exception {
        try (var f = new NoItemFixture(RoomMode.NO_ITEM, RoomRule.POWER_QUIZ, 4)) {
            f.apply(f.shot());
            var original = f.snapshot().loot();
            var current = original.quizOpportunities().get(2);
            var stale = new PowerQuizOpportunity(
                current.sessionId(),
                UUID.randomUUID(),
                current.slotIndex(),
                current.sourceSlot(),
                current.turnRevision(),
                current.scenarioHash(),
                current.pickerScalar(),
                false,
                current.knownGaugeValues()
            );
            f.f.match.updateLoot(new MatchLootState(
                original.inventories(),
                original.offers(),
                Map.of(2, stale),
                original.grantSequence()
            ));
            f.f.runtime.refresh();
            assertFalse(f.report(f.quiz(2, 0)));
            f.f.match.updateLoot(original);
            f.f.runtime.refresh();
            f.f.timers.now = f.snapshot().completion().orElseThrow().deadlineNanos();
            assertFalse(f.report(f.quiz(2, 0)));
            f.f.timers.now = 0;
            f.f.match.updateLoot(original.without(f.context(3).sessionId()));
            f.f.runtime.refresh();

            assertTrue(f.report(f.quiz(2, 0)));
            assertEquals(5, f.snapshot().world().teamGauges().get(RoomTeam.RED).committed());
            assertFalse(f.snapshot().loot().quizOpportunities().containsKey(3));
        }
    }

    private ScenarioTeamGaugeClaim gauge(int slot, int kind, int reason, int old, int value) {
        return new ScenarioTeamGaugeClaim(new ScenarioEventTime(slot, kind, 0, 0), 0, reason, value, old);
    }

    private ReportMatchBonusCommand claim(
        NoItemFixture f,
        Optional<ScenarioTeamGaugeClaim> gauge,
        int nested,
        int item,
        long entity
    ) {
        return new ReportMatchBonusCommand(f.context(2), 2, nested, gauge, entity, item, 0, 0, 0);
    }
}
