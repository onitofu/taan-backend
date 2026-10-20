package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.RoomScenario;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomTeam;

class TeamMatchTest {
    @Test
    void keepsBothNoItemModesInPreviewWithMixedWeaponsAndFullTeams() {
        for (var mode : List.of(RoomMode.NO_ITEM, RoomMode.CAPTAIN_NO_ITEM)) {
            try (var room = new MatchRoomFixture(new RoomScenario(MatchExecutionMode.CLIENT_SCENARIO), 8, mode)) {
                for (int index = 0; index < room.players.size(); index++) {
                    room.scenario.rooms.setReady(room.players.get(index), false);
                    room.scenario.rooms.changeWeapon(
                        room.players.get(index),
                        index + 1,
                        (new int[]{0, 3, 8, 10})[index % 4]
                    );
                    room.scenario.matches.subscribe(room.players.get(index), new TestMatchOutput());
                }

                room.players.stream().skip(1).forEach(id -> room.scenario.rooms.setReady(id, true));

                if (mode.requiresCaptainSelection()) {
                    room.captains();
                }

                var result = room.scenario.start.execute(room.owner);
                assertEquals("STARTED", result.reason());
                result.delivery().toCompletableFuture().join();
                var token = room.scenario.rooms.matchContext(room.owner).token();
                MatchFixtures.loaded(room.scenario.matches, token);
                assertEquals(MatchPhase.PREVIEW, room.scenario.matches.current(token).orElseThrow().phase());
            }
        }
    }

    @Test
    void bootsAllSixteenTeamSizesInBothItemModesAndPreservesOneTurnPerLivingPlayerPerRound() {
        for (var mode : List.of(RoomMode.ITEM, RoomMode.CAPTAIN_ITEM)) {
            for (int blue = 1; blue <= 4; blue++) {
                for (int red = 1; red <= 4; red++) {
                    try (var f = new TeamMatchFixture(blue, red, mode)) {
                        f.start(true);
                        var before = f.snapshot();
                        assertEquals(MatchPhase.ACTION, before.phase());
                        assertEquals(blue + red, before.participants().size());
                        assertEquals(
                            blue,
                            before.participants().stream().filter(p -> p.team() == RoomTeam.BLUE).count()
                        );
                        assertEquals(blue + red, before.loadedSessions().size());
                        var seen = new HashSet<Integer>();

                        for (int step = 0; step < blue + red; step++) {
                            assertTrue(seen.add(f.active().slotIndex()));
                            assertEquals(before.turn().windRaw(), f.snapshot().turn().windRaw());
                            int expected = f.snapshot().definitions().weapons().get(
                                f.active().weaponId()
                            ).resourceMove();
                            assertEquals(expected, f.snapshot().movements().get(f.active().sessionId())
                                .allowance().initialBudgetMillis());
                            f.pass();
                        }

                        assertEquals(Set.copyOf(before.turn().order()), seen);
                        assertEquals(before.turn().round() + 1, f.snapshot().turn().round());
                        assertTrue(Math.abs(f.snapshot().turn().windRaw() - before.turn().windRaw()) <= 20);
                    }
                }
            }
        }
    }

    @Test
    void keepsADeadOrdinaryPlayerWatchingAndRequiresOneAckFromEveryConnectedPlayer() throws Exception {
        for (var mode : List.of(RoomMode.ITEM, RoomMode.CAPTAIN_ITEM)) {
            try (var f = new TeamMatchFixture(2, 2, mode)) {
                f.start(true);
                var shooter = f.active();
                var victim = f.snapshot().participants().stream().filter(p -> p.team() != shooter.team())
                    .filter(p -> !f.snapshot().captain(p.slotIndex())).findFirst().orElseThrow();

                assertEquals(ScenarioApplicationOutcome.APPLIED, f.shot(victim.slotIndex(), 10000).outcome());
                var committed = f.snapshot();
                assertTrue(committed.actors().get(victim.sessionId()).dead());
                assertTrue(committed.resolution().pendingOutcome().isEmpty());
                assertEquals(4, committed.completion().orElseThrow().awaitingSessions().size());
                assertTrue(f.outputs.get(shooter.sessionId()).scenarios.isEmpty());
                assertEquals(3, f.outputs.values().stream().mapToInt(o -> o.scenarios.size()).sum());

                var action = new ReportMatchGhostAction(f.scenario.matches);
                action.execute(
                    victim.sessionId(),
                    f.scenario.rooms.matchContext(victim.sessionId()),
                    victim.slotIndex()
                )
                    .toCompletableFuture().join();
                assertEquals(committed, f.snapshot());
                assertTrue(f.ack(shooter.sessionId()));
                assertFalse(f.ack(shooter.sessionId()));

                for (var id : f.players) {
                    if (!id.equals(shooter.sessionId()) && !id.equals(victim.sessionId())) {
                        assertTrue(f.ack(id));
                    }
                }

                assertEquals(MatchPhase.COMPLETION, f.snapshot().phase());
                assertTrue(f.ack(victim.sessionId()));
                assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                assertTrue(f.outputs.get(victim.sessionId()).closed.isEmpty());

                for (int index = 0; index < 6; index++) {
                    assertFalse(f.active().sessionId().equals(victim.sessionId()));
                    f.pass();
                }
            }
        }
    }

    @Test
    void endsCaptainCombatOnCaptainDeathWhileItemCombatWaitsForTheWholeTeam() throws Exception {
        for (var mode : List.of(RoomMode.ITEM, RoomMode.CAPTAIN_ITEM)) {
            try (var f = new TeamMatchFixture(2, 2, mode)) {
                f.start(true);
                var shooter = f.active();
                var target = f.snapshot().participants().stream().filter(p -> p.team() != shooter.team())
                    .filter(
                        p -> mode == RoomMode.ITEM || f.snapshot().captain(p.slotIndex())
                    ).findFirst().orElseThrow();
                assertEquals(ScenarioApplicationOutcome.APPLIED, f.shot(target.slotIndex(), 10000).outcome());

                if (mode.requiresCaptainSelection()) {
                    assertEquals(Set.of(shooter.team()), f.snapshot().resolution().pendingOutcome()
                        .orElseThrow().winningTeams());
                } else {
                    assertTrue(f.snapshot().resolution().pendingOutcome().isEmpty());
                }

                f.acknowledge();

                if (mode.requiresCaptainSelection()) {
                    assertTrue(f.scenario.matches.current(f.token).isEmpty());
                    assertTrue(f.outputs.values().stream().allMatch(o -> o.ends == 1));
                    f.start(true);
                    assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                } else {
                    assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                }
            }
        }
    }

    @Test
    void continuesAfterAnActiveOrWaitingPlayerDisconnectsAndEndsAfterTheCaptainDisconnects() {
        for (boolean active : new boolean[]{true, false}) {
            try (var f = new TeamMatchFixture(2, 2, RoomMode.ITEM)) {
                f.start(true);
                var member = active ? f.active() : f.snapshot().participants().stream()
                    .filter(p -> !p.sessionId().equals(f.active().sessionId())).findFirst().orElseThrow();
                f.scenario.matches.unsubscribe(member.sessionId());
                f.scenario.rooms.release(member.sessionId(), "PEER_CLOSED");

                assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                assertTrue(f.snapshot().resolution().exits().containsKey(member.sessionId()));

                for (int step = 0; step < 6; step++) {
                    assertFalse(f.active().sessionId().equals(member.sessionId()));
                    f.pass();
                }
            }
        }

        try (var f = new TeamMatchFixture(2, 2, RoomMode.CAPTAIN_ITEM)) {
            f.start(true);
            var captain = f.snapshot().participants().stream().filter(p -> f.snapshot().captain(p.slotIndex()))
                .findFirst().orElseThrow();
            f.scenario.matches.unsubscribe(captain.sessionId());
            f.scenario.rooms.release(captain.sessionId(), "PEER_CLOSED");
            assertTrue(f.scenario.matches.current(f.token).isEmpty());
            assertTrue(f.outputs.entrySet().stream().filter(e -> !e.getKey().equals(captain.sessionId()))
                .allMatch(e -> e.getValue().ends == 1));
        }
    }
}
