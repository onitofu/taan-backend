package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.match.service.model.ActivateSpecialCommand;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.match.service.model.SpentMatchItem;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.MoveMatchActorCommand;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchSpecialAdmissionTest {
    @Test
    void ignoresForeignStaleExpiredDeadAndUnstartedCommandsWithoutEndingAnotherTurn() throws Exception {
        try (var e = new EnergyRuntimeFixture()) {
            var use = e.use();
            var c = use.context();
            var other = e.snapshot().participants().get(1);
            assertFalse(e.f.match.activateSpecial(other.sessionId()));
            var commands = List.of(
                new ActivateSpecialCommand(
                    new MatchCommandContext(
                        c.matchId(),
                        c.roomId(),
                        c.roomGeneration(),
                        c.channelId() + 1,
                        c.sessionId(),
                        c.admissionId()
                    ),
                    use.turnRevision(),
                    use.phaseRevision(),
                    0,
                    1,
                    0,
                    0,
                    255,
                    6
                ),
                new ActivateSpecialCommand(
                    MatchCommandContext.from(e.snapshot(), other.sessionId()),
                    use.turnRevision(),
                    use.phaseRevision(),
                    2,
                    2,
                    0,
                    0,
                    255,
                    6
                ),
                new ActivateSpecialCommand(c, use.turnRevision() + 1, use.phaseRevision(), 0, 1, 0, 0, 255, 6),
                new ActivateSpecialCommand(c, use.turnRevision(), use.phaseRevision() + 1, 0, 1, 0, 0, 255, 6),
                new ActivateSpecialCommand(c, use.turnRevision(), use.phaseRevision(), 2, 1, 0, 0, 255, 6)
            );

            for (var command : commands) {
                assertFalse(e.activate(command));
            }

            var spend = e.spend(List.of());
            assertFalse(e.record(new RecordMatchSpendCommand(
                spend.context(),
                spend.turnRevision() + 1,
                spend.phaseRevision(),
                spend.slot(),
                spend.teamClear11(),
                spend.teamClear12(),
                spend.items()
            )));
            e.f.admitted = false;
            assertFalse(e.activate(use));
            e.f.admitted = true;
            e.f.timers.now = e.snapshot().deadlineNanos();
            assertFalse(e.activate(use));
            assertNull(e.f.callbacks.reason);
            e.f.runtime.stop();
            e.f.match.cancel();
            assertFalse(e.f.match.activateSpecial(e.f.room.owner));
        }

        try (var e = new EnergyRuntimeFixture()) {
            var actor = e.snapshot().actors().get(e.f.room.owner);
            int bottom = e.snapshot().definitions().map().height() + 200;
            assertTrue(e.movement.move(new MoveMatchActorCommand(
                e.context(),
                e.snapshot().turn().revision(),
                e.snapshot().phaseRevision(),
                0,
                actor.x(),
                bottom,
                actor.facing(),
                MatchMovementKind.MOVE
            )));
            e.f.match.settleMovement(e.snapshot().movements().get(e.f.room.owner).fall().orElseThrow().endsAtNanos());
            e.f.runtime.refresh();
            assertTrue(e.snapshot().actors().get(e.f.room.owner).dead());
            assertFalse(e.f.match.activateSpecial(e.f.room.owner));
            assertFalse(e.activate(e.use()));
            assertNull(e.f.callbacks.reason);
        }

        try (var f = new ScenarioRuntimeFixture()) {
            var s = f.f.runtime.snapshot();
            var result = f.f.runtime.turnWork(() -> f.f.runtime.items.activate(new ActivateSpecialCommand(
                f.f.context(),
                s.turn().revision(),
                s.phaseRevision(),
                0,
                1,
                0,
                0,
                255,
                6
            )));
            f.f.run();
            assertFalse(result.join());
        }
    }

    @Test
    void rejectsEveryInvalidSpecialRouteAndEveryUnauthorizedSpendList() throws Exception {
        for (int[] fields : new int[][]{
            {1, 0, 0, 255, 5}, {2, 0, 0, 255, 6}, {1, 1, 0, 255, 6}, {1, 0, 1, 255, 6}, {1, 0, 0, 0, 6}
        }) {
            try (var e = new EnergyRuntimeFixture()) {
                e.charge();
                var use = e.use();
                assertFalse(e.activate(new ActivateSpecialCommand(
                    use.context(),
                    use.turnRevision(),
                    use.phaseRevision(),
                    use.slot(),
                    fields[0],
                    fields[1],
                    fields[2],
                    fields[3],
                    fields[4]
                )));
                assertEquals(RoomMatchEndReason.INVALID_SCENARIO, e.f.callbacks.reason);
                assertTrue(e.f.owner.specials.isEmpty());
            }
        }

        for (int index = 0; index < 8; index++) {
            try (var e = new EnergyRuntimeFixture()) {
                e.charge();
                if (index != 7) {
                    assertTrue(e.activate(e.use()));
                }
                var values = switch (index) {
                    case 2 -> List.of(new SpentMatchItem(5, 0));
                    case 3 -> List.of(new SpentMatchItem(6, -1));
                    case 4 -> List.of(new SpentMatchItem(6, 256));
                    case 5 -> List.of(new SpentMatchItem(6, 0), new SpentMatchItem(6, 1));
                    case 6 -> List.of(
                        new SpentMatchItem(6, 0),
                        new SpentMatchItem(6, 1),
                        new SpentMatchItem(6, 2),
                        new SpentMatchItem(6, 3)
                    );
                    case 7 -> List.of(new SpentMatchItem(6, 0));
                    default -> List.<SpentMatchItem>of();
                };
                var s = e.spend(values);
                assertFalse(e.record(new RecordMatchSpendCommand(
                    s.context(),
                    s.turnRevision(),
                    s.phaseRevision(),
                    s.slot(),
                    index == 0 ? 2 : s.teamClear11(),
                    index == 1 ? 2 : s.teamClear12(),
                    s.items()
                )));
                assertEquals(RoomMatchEndReason.INVALID_SCENARIO, e.f.callbacks.reason);
                assertTrue(e.f.owner.spends.isEmpty());
            }
        }

        try (var e = new EnergyRuntimeFixture()) {
            e.charge();
            assertTrue(e.record(e.spend(List.of())));
            assertFalse(e.activate(e.use()));
        }
    }

    @Test
    void acceptsEitherOwnTeamClearFieldAndDiscardsARecordAfterAdmissionChanges() throws Exception {
        try (var e = new EnergyRuntimeFixture()) {
            var spend = e.spend(List.of());
            assertTrue(e.record(new RecordMatchSpendCommand(
                spend.context(),
                spend.turnRevision(),
                spend.phaseRevision(),
                spend.slot(),
                255,
                1,
                List.of()
            )));
            var shot = e.f.runtime.applyScenario(e.shot(false, 0));
            e.f.run();
            assertTrue(shot.join().committed().isPresent());
            assertFalse(e.activate(e.use()));
        }

        try (var e = new EnergyRuntimeFixture()) {
            var accesses = new AtomicInteger();
            e.f.onAccess = () -> {
                if (accesses.incrementAndGet() == 2) {
                    e.f.admitted = false;
                }
            };
            assertFalse(e.record(e.spend(List.of())));
            assertTrue(e.f.owner.spends.isEmpty());
            assertNull(e.f.callbacks.reason);
        }
    }

    @Test
    void revalidatesAdmissionBeforeResettingEnergyAndPreservesTheOtherPlayersSelectedSpecial() throws Exception {
        try (var e = new EnergyRuntimeFixture()) {
            e.charge();
            var accesses = new AtomicInteger();
            e.f.onAccess = () -> {
                if (accesses.incrementAndGet() == 2) {
                    e.f.admitted = false;
                }
            };
            assertFalse(e.activate(e.use()));
            assertEquals(100, e.snapshot().actors().get(e.f.room.owner).energy());
            assertNull(e.f.callbacks.reason);
        }

        try (var e = new EnergyRuntimeFixture()) {
            e.charge();
            var first = e.use();
            assertTrue(e.activate(first));
            assertTrue(e.movement.pass(e.movement.pass()));
            assertTrue(e.activate(e.use()));
            assertTrue(e.record(e.spend(List.of())));
            var applied = e.f.runtime.applyScenario(e.shot(true, 0));
            e.f.run();
            assertTrue(applied.join().committed().isPresent());
            assertEquals(1, e.snapshot().attacks().size());
            assertTrue(e.snapshot().attacks().containsKey(first.context().sessionId()));
        }
    }
}
