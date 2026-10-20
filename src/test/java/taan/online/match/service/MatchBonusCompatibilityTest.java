package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.match.model.MatchExitKind;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.ReportMatchBonusCommand;
import taan.online.match.service.model.ReportMatchPickupCommand;

class MatchBonusCompatibilityTest {
    @Test
    void checksAllActorBindingsAndLeavesTheWorldAndInventoryUnchangedWithoutEchoingBonuses() throws Exception {
        try (var live = new LiveMatchFixture(); var logs = new LogCapture()) {
            var f = live.f;
            var before = f.runtime.snapshot();
            var c = f.context();
            assertTrue(report(f, new ReportMatchBonusCommand(c, 0, 0, OptionalInt.empty(), -1L, 5, 0, 0, 0)));
            assertTrue(report(f, new ReportMatchBonusCommand(c, 0, 0, OptionalInt.of(0), -1L, 5, 0, 0, 0)));
            assertFalse(report(f, new ReportMatchBonusCommand(c, 2, 2, OptionalInt.empty(), -1L, 5, 0, 0, 0)));
            assertFalse(report(f, new ReportMatchBonusCommand(c, 0, 2, OptionalInt.empty(), -1L, 5, 0, 0, 0)));
            assertFalse(report(f, new ReportMatchBonusCommand(c, 0, 0, OptionalInt.of(2), -1L, 5, 0, 0, 0)));
            assertFalse(report(f, new ReportMatchBonusCommand(c, 0, 0, OptionalInt.empty(), 123, 5, 0, 0, 0)));
            assertEquals(before, f.runtime.snapshot());
            assertTrue(f.owner.specials.isEmpty());
            assertTrue(f.owner.spends.isEmpty());
            assertTrue(f.owner.closed.isEmpty());
            assertTrue(logs.text().contains("match.bonus.ignored"));
            assertFalse(logs.text().contains("itemId"));

            f.admitted = false;
            assertFalse(report(f, command(c)));
            f.admitted = true;
            f.match.withdraw(c.sessionId(), MatchExitKind.SURRENDERED);
            f.runtime.refresh();
            assertFalse(report(f, command(c)));
        }
    }

    @Test
    void ignoresMissingMatchesPreviewAndUnstartedProfilesButAcceptsReportsDuringCompletionAndSettlement()
        throws Exception {
        try (var f = new MatchRuntimeFixture(8)) {
            assertFalse(report(f, command(f.context())));
            assertFalse(MatchItemExecution.reportBonus(null, command(f.context())).toCompletableFuture().join());
            assertFalse(new ReportMatchBonusAction(f.room.scenario.matches).execute(command(f.context()))
                .toCompletableFuture().join());
        }

        try (var f = new MatchRuntimeFixture(8, MatchExecutionMode.CLIENT_SCENARIO)) {
            assertFalse(report(f, command(f.context())));
        }

        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            assertTrue(m.move(m.move(0, -20, 0, MatchMovementKind.MOVE)));
            assertTrue(m.pass(m.pass()));
            assertTrue(report(f, command(f.context())));
            var pickup = MatchItemExecution.reportPickup(
                f.runtime,
                new ReportMatchPickupCommand(f.context(), 0, 0, OptionalInt.empty(), 123, 5)
            );
            f.run();
            assertTrue(pickup.toCompletableFuture().join());
        }

        try (var live = new LiveMatchFixture()) {
            var f = live.f;
            f.runtime.applyScenario(live.shot(false, false));
            f.run();
            assertTrue(report(f, command(f.context())));
            var pickup = MatchItemExecution.reportPickup(
                f.runtime,
                new ReportMatchPickupCommand(f.context(), 0, 0, OptionalInt.empty(), 123, 5)
            );
            f.run();
            assertTrue(pickup.toCompletableFuture().join());
        }
    }

    private ReportMatchBonusCommand command(MatchCommandContext context) {
        return new ReportMatchBonusCommand(context, 0, 0, OptionalInt.empty(), -1L, 5, 0, 0, 0);
    }

    private boolean report(MatchRuntimeFixture f, ReportMatchBonusCommand command) {
        var result = MatchItemExecution.reportBonus(f.runtime, command);
        f.run();
        return result.toCompletableFuture().join();
    }
}
