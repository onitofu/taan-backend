package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchOutcomeFailureTest {
    @Test
    void releasesARepeatedDepartureWhenItsHandoffFailsWithoutLeakingAnExceptionMessage() {
        var wrapper = new AtomicReference<ControlledDepartureLifecycle>();
        var scenario = new RoomScenario(
            MatchExecutionMode.CANNON_1V1,
            lifecycle -> {
                var controlled = new ControlledDepartureLifecycle(lifecycle);
                wrapper.set(controlled);
                return controlled;
            }
        );

        try (var f = new MatchOutcomeFixture(scenario); var logs = new LogCapture()) {
            var member = scenario.rooms.membership(f.room.owner).orElseThrow();
            f.disconnect(f.room.owner);
            scenario.rooms.release(member, "REPEATED_EOF");
            assertEquals(0, f.guest.ends);
            wrapper.get().departure.completeExceptionally(new IllegalStateException("handoff-secret"));
            assertEquals(1, f.guest.ends);
            assertTrue(scenario.rooms.membership(f.room.owner).isEmpty());
            assertTrue(f.guest.outcomes.getFirst().winningTeams().isEmpty());
            assertTrue(f.matches.current(f.token).isEmpty());
            assertFalse(logs.text().contains("handoff-secret"));
        }
    }

    @Test
    void ignoresALateHandoffFailureAfterAnotherMatchWasStartedInTheSameRoom() {
        var wrapper = new AtomicReference<ControlledDepartureLifecycle>();
        var scenario = new RoomScenario(
            MatchExecutionMode.CANNON_1V1,
            lifecycle -> {
                var controlled = new ControlledDepartureLifecycle(lifecycle);
                wrapper.set(controlled);
                return controlled;
            }
        );

        try (var f = new MatchOutcomeFixture(scenario)) {
            f.disconnect(f.room.players.get(1));
            scenario.rooms.terminateMatch(f.token, RoomMatchEndReason.INVALID_SCENARIO);
            var newcomer = scenario.player(3, 1);
            scenario.enter.execute(newcomer, 1, "", new RecordingRoomOutput());
            f.matches.subscribe(newcomer, new TestMatchOutput());
            scenario.rooms.setReady(newcomer, true);
            f.start();
            wrapper.get().departure.completeExceptionally(new IllegalStateException("late-failure"));
            assertTrue(f.matches.current(f.token).isPresent());
            assertEquals(1, f.owner.ends);
        }
    }

    @Test
    void handlesASynchronousHandoffFailureAndANullOrRejectedResponseWithoutLeavingAMatchLocked() {
        for (int mode = 0; mode < 3; mode++) {
            var wrapper = new AtomicReference<ControlledDepartureLifecycle>();
            var scenario = new RoomScenario(
                MatchExecutionMode.CANNON_1V1,
                lifecycle -> {
                    var controlled = new ControlledDepartureLifecycle(lifecycle);
                    wrapper.set(controlled);
                    return controlled;
                }
            );

            try (var f = new MatchOutcomeFixture(scenario)) {
                if (mode == 0) {
                    wrapper.get().directFailure = new IllegalStateException("request-failed");
                }
                f.disconnect(f.room.owner);

                if (mode != 0) {
                    wrapper.get().departure.complete(mode == 1 ? false : null);
                }
                assertEquals(1, f.guest.ends);
                assertTrue(f.matches.current(f.token).isEmpty());
                assertTrue(scenario.rooms.membership(f.room.owner).isEmpty());
            }
        }
    }
}
