package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.match.model.MatchExitKind;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.model.RoomMode;

class TeamMatchDeadlineTest {
    @Test
    void excludesAnUnloadedOrdinaryParticipantAndStartsBothTeamsWithoutRestartingTheClock() {
        for (var mode : List.of(RoomMode.ITEM, RoomMode.CAPTAIN_ITEM)) {
            try (var f = new TeamMatchFixture(2, 2, mode)) {
                f.start(false);
                var before = f.snapshot();
                var missing = before.participants().stream().filter(p -> !before.captain(p.slotIndex()))
                    .findFirst().orElseThrow();

                for (var member : before.participants()) {
                    if (!member.sessionId().equals(missing.sessionId())) {
                        f.scenario.matches.loadingDone(
                            f.scenario.rooms.matchContext(member.sessionId()),
                            member.slotIndex(),
                            member.spawn().settledX(),
                            member.spawn().settledY()
                        ).toCompletableFuture().join();
                    }
                }

                assertEquals(MatchPhase.LOADING, f.snapshot().phase());
                f.scenario.matchTimers.advanceSeconds(31);

                assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                assertEquals(MatchExitKind.DEPARTED, f.snapshot().resolution().exits().get(missing.sessionId()));
                assertFalse(f.snapshot().turn().order().contains(missing.slotIndex()));
                assertEquals(List.of("MATCH_LOADING_TIMEOUT"), f.outputs.get(missing.sessionId()).closed);
                f.scenario.rooms.release(missing.sessionId(), "PEER_CLOSED");
                assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                f.pass();
                assertEquals(2, f.snapshot().turn().revision());
            }
        }
    }

    @Test
    void cancelsWhenAnEntireTeamOrItsCaptainHasNotLoaded() {
        for (var mode : List.of(RoomMode.ITEM, RoomMode.CAPTAIN_ITEM)) {
            try (var f = new TeamMatchFixture(2, 2, mode)) {
                f.start(false);
                var snapshot = f.snapshot();
                var missing = snapshot.participants().stream()
                    .filter(p -> mode == RoomMode.ITEM || snapshot.captain(p.slotIndex())).findFirst().orElseThrow();

                for (var member : snapshot.participants()) {
                    boolean exclude = mode == RoomMode.ITEM ? member.team() == missing.team()
                        : member.sessionId().equals(missing.sessionId());

                    if (!exclude) {
                        f.scenario.matches.loadingDone(
                            f.scenario.rooms.matchContext(member.sessionId()),
                            member.slotIndex(),
                            member.spawn().settledX(),
                            member.spawn().settledY()
                        ).toCompletableFuture().join();
                    }
                }

                f.scenario.matchTimers.advanceSeconds(31);
                assertTrue(f.scenario.matches.current(f.token).isEmpty());
                assertTrue(f.outputs.values().stream().allMatch(o -> o.ends == 0));
            }
        }
    }

    @Test
    void excludesOnlyAMissingScenarioAckAndKeepsTheRemainingTeamsPlaying() throws Exception {
        try (var f = new TeamMatchFixture(2, 2, RoomMode.ITEM)) {
            f.start(true);
            var shooter = f.active();
            var target = f.snapshot().participants().stream().filter(p -> p.team() != shooter.team())
                .findFirst().orElseThrow();
            assertEquals(ScenarioApplicationOutcome.APPLIED, f.shot(target.slotIndex(), 0).outcome());
            var pending = f.snapshot().completion().orElseThrow();

            for (var id : pending.awaitingSessions()) {
                if (!id.equals(target.sessionId())) {
                    assertTrue(f.ack(id));
                }
            }

            f.scenario.matchTimers.advanceSeconds(20);
            assertEquals(MatchPhase.ACTION, f.snapshot().phase());
            assertEquals(MatchExitKind.DEPARTED, f.snapshot().resolution().exits().get(target.sessionId()));
            assertEquals(List.of("SCENARIO_COMPLETION_TIMEOUT"), f.outputs.get(target.sessionId()).closed);
            assertTrue(f.outputs.entrySet().stream().filter(e -> !e.getKey().equals(target.sessionId()))
                .allMatch(e -> e.getValue().closed.isEmpty()));
            int delivered = f.outputs.get(target.sessionId()).scenarios.size();
            assertEquals(ScenarioApplicationOutcome.APPLIED, f.shot(shooter.slotIndex(), 0).outcome());
            assertEquals(3, f.snapshot().completion().orElseThrow().awaitingSessions().size());
            assertEquals(delivered, f.outputs.get(target.sessionId()).scenarios.size());
            f.acknowledge();
        }
    }
}
