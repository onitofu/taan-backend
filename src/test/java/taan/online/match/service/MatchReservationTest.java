package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.service.model.RoomMatchReservationOutcome.COMMITTED;
import static taan.online.room.service.model.RoomMatchReservationOutcome.RESERVED;
import static taan.online.room.service.model.RoomMatchReservationOutcome.ROLLED_BACK;
import static taan.online.room.service.model.RoomMatchReservationOutcome.STALE;
import static taan.online.room.service.model.RoomMatchReservationOutcome.UNCHANGED;

import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchRoomFixture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.lobby.view.LobbyRoomListMapper;
import taan.online.match.model.MatchPhase;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.view.RoomSnapshotMapper;

class MatchReservationTest {
    @Test
    void freezesRealIdentitiesAppearanceStatsAndUniqueSpawnsForAllModes() {
        for (var mode : RoomMode.values()) {
            for (int count : new int[]{2, 8}) {
                try (var f = new MatchRoomFixture(count, mode); var logs = new LogCapture()) {
                    if (mode.requiresCaptainSelection()) {
                        f.captains();
                    }

                    var room = f.scenario.rooms.current(f.owner).orElseThrow();
                    var result = f.scenario.rooms.reserveMatch(f.owner);
                    var token = result.token();
                    var match = f.scenario.matches.current(token).orElseThrow();

                    assertEquals(RESERVED, result.outcome());
                    assertEquals(MatchPhase.RESERVED, match.phase());
                    assertEquals(room.id(), match.roomId());
                    assertEquals(room.generation(), match.roomGeneration());
                    assertEquals(1, match.channelId());
                    assertEquals(f.owner, match.ownerSessionId());
                    assertEquals(mode, match.mode());
                    assertEquals(room.settings().rule(), match.rule());
                    assertEquals(mode.requiresCaptainSelection(), match.captains() != null);
                    assertEquals(RoomState.MATCH, f.scenario.rooms.current(f.owner).orElseThrow().state());
                    assertEquals(2, new LobbyRoomListMapper().map(f.scenario.rooms.forChannel(1).getFirst()).state());
                    assertEquals(
                        2,
                        new RoomSnapshotMapper().map(f.scenario.rooms.current(f.owner).orElseThrow())
                            .info().state()
                    );
                    assertThrows(UnsupportedOperationException.class, () -> match.participants().clear());
                    var spawns = new HashSet<Integer>();

                    for (var participant : match.participants()) {
                        var source = room.slots().get(participant.slotIndex()).participant();
                        var membership = f.scenario.rooms.membership(participant.sessionId()).orElseThrow();

                        assertEquals(source.character(), participant.character());
                        assertEquals(membership.admissionId(), participant.admissionId());
                        assertEquals(room.slots().get(participant.slotIndex()).team(), participant.team());
                        assertEquals(0, participant.weaponId());
                        assertEquals(1000, participant.hp());
                        assertEquals(1000, participant.maxHp());
                        assertEquals(0, participant.energy());
                        assertEquals(100, participant.maxEnergy());
                        assertTrue(spawns.add(participant.spawn().index()));
                    }

                    assertEquals(count, spawns.size());
                    assertEquals(UNCHANGED, f.scenario.rooms.reserveMatch(f.owner).outcome());
                    assertTrue(f.scenario.timers.tasks.isEmpty());
                    assertTrue(logs.text().contains("match.reserved"));
                    assertFalse(logs.text().contains("Hero"));
                    assertEquals(COMMITTED, f.scenario.rooms.commitMatch(token).toCompletableFuture().join().outcome());
                    assertEquals(UNCHANGED, f.scenario.rooms.commitMatch(token).toCompletableFuture().join().outcome());
                    assertEquals(MatchPhase.LOADING, f.scenario.matches.current(token).orElseThrow().phase());
                    assertEquals(MatchPhase.RESERVED, match.phase());
                    assertEquals(ROLLED_BACK, f.scenario.rooms.rollbackMatch(token, "TEST_CANCELLED").outcome());
                    assertEquals(STALE, f.scenario.rooms.rollbackMatch(token, "REPLAY").outcome());
                    assertEquals(STALE, f.scenario.rooms.commitMatch(token).toCompletableFuture().join().outcome());
                    assertTrue(f.scenario.matches.current(token).isEmpty());
                    assertEquals(RoomState.WAITING, f.scenario.rooms.current(f.owner).orElseThrow().state());
                    assertTrue(
                        f.scenario.rooms.current(f.owner).orElseThrow().slots().stream()
                            .noneMatch(slot -> slot.state() == RoomSlotState.READY)
                    );
                }
            }
        }
    }

    @Test
    void cancelsOnDeparturePromotesOwnerAndRejectsOldTokensAfterReuse() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var room = f.scenario.rooms.current(f.owner).orElseThrow();
            var token = f.scenario.rooms.reserveMatch(f.owner).token();
            var guest = f.players.get(1);
            var oldMembership = f.scenario.rooms.membership(f.owner).orElseThrow();
            f.scenario.rooms.release(f.owner, "DISCONNECTED");

            assertTrue(f.scenario.matches.current(token).isEmpty());
            assertEquals(guest, f.scenario.rooms.current(guest).orElseThrow().ownerSessionId());
            assertEquals(RoomState.WAITING, f.scenario.rooms.current(guest).orElseThrow().state());
            f.scenario.rooms.release(guest, "DISCONNECTED");
            f.scenario.lobby(f.owner);
            var next = f.scenario.create.execute(f.owner, RoomScenario.command(), new RecordingRoomOutput()).room();

            assertEquals(room.id(), next.id());
            assertNotEquals(room.generation(), next.generation());
            assertEquals(STALE, f.scenario.rooms.commitMatch(token).toCompletableFuture().join().outcome());
            f.scenario.rooms.release(oldMembership, "LATE");

            assertEquals(next, f.scenario.rooms.current(f.owner).orElseThrow());
        }
    }

    @Test
    void ignoresForgedTokensAndCancelsBothReservedAndLoadingMatchesOnShutdown() {
        for (boolean commit : new boolean[]{true, false}) {
            try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
                var token = f.scenario.rooms.reserveMatch(f.owner).token();

                for (var wrong : new RoomMatchToken[]{
                    new RoomMatchToken(token.roomId(), UUID.randomUUID(), token.matchId()),
                    new RoomMatchToken(token.roomId(), token.generation(), UUID.randomUUID())
                }) {
                    assertEquals(STALE, f.scenario.rooms.commitMatch(wrong).toCompletableFuture().join().outcome());
                    assertEquals(STALE, f.scenario.rooms.rollbackMatch(wrong, "FORGED").outcome());
                }

                if (commit) {
                    f.scenario.rooms.commitMatch(token).toCompletableFuture().join();
                }

                f.scenario.rooms.close();
                f.scenario.rooms.close();

                assertTrue(f.scenario.matches.current(token).isEmpty());
                assertTrue(f.scenario.rooms.current(f.owner).isEmpty());
                assertEquals(STALE, f.scenario.rooms.rollbackMatch(token, "AFTER_CLOSE").outcome());
            }
        }
    }
}
