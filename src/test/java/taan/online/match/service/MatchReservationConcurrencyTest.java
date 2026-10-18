package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.service.model.RoomMatchReservationOutcome.RESERVED;
import static taan.online.room.service.model.RoomMatchReservationOutcome.UNCHANGED;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.MatchRoomFixture;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomState;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.ExpelRoomParticipantOutcome;
import taan.online.room.service.model.SetRoomReadyOutcome;

class MatchReservationConcurrencyTest {
    @Test
    void createsExactlyOneRuntimeForConcurrentStartsAndSerializesReadyAndExpel() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
                    var barrier = new CyclicBarrier(2);
                    var first = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return f.scenario.rooms.reserveMatch(f.owner);
                        }
                    );
                    var second = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return f.scenario.rooms.reserveMatch(f.owner);
                        }
                    );
                    var a = first.get(5, TimeUnit.SECONDS);
                    var b = second.get(5, TimeUnit.SECONDS);

                    assertNotEquals(a.outcome(), b.outcome());
                    assertTrue(List.of(RESERVED, UNCHANGED).containsAll(List.of(a.outcome(), b.outcome())));
                    assertEquals(a.token(), b.token());
                    assertEquals(RoomState.MATCH, f.scenario.rooms.current(f.owner).orElseThrow().state());
                }

                for (boolean expel : new boolean[]{false, true}) {
                    try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
                        var barrier = new CyclicBarrier(2);
                        var reservation = workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return f.scenario.rooms.reserveMatch(f.owner);
                            }
                        );
                        var mutation = workers.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return expel ? f.scenario.rooms.expel(f.owner, 2).outcome().name()
                                    : f.scenario.rooms.setReady(f.players.get(1), false).outcome().name();
                            }
                        );
                        var result = reservation.get(5, TimeUnit.SECONDS);
                        var change = mutation.get(5, TimeUnit.SECONDS);

                        if (result.outcome() == RESERVED) {
                            assertEquals("REJECTED", change);
                            assertTrue(f.scenario.matches.current(result.token()).isPresent());
                        } else {
                            assertEquals(
                                expel ? ExpelRoomParticipantOutcome.EXPELLED.name()
                                    : SetRoomReadyOutcome.CHANGED.name(),
                                change
                            );
                            assertEquals(RoomState.WAITING, f.scenario.rooms.current(f.owner).orElseThrow().state());
                        }
                    }
                }
            }
        }
    }

    @Test
    void neverLeavesAnOrphanWhenReservationRacesWithDeparture() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
                    var barrier = new CyclicBarrier(2);
                    var reservation = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return f.scenario.rooms.reserveMatch(f.owner);
                        }
                    );
                    var departure = workers.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            f.scenario.rooms.release(f.players.get(1), "DISCONNECTED");
                            return true;
                        }
                    );
                    var result = reservation.get(5, TimeUnit.SECONDS);
                    assertTrue(departure.get(5, TimeUnit.SECONDS));

                    if (result.token() != null) {
                        assertTrue(f.scenario.matches.current(result.token()).isEmpty());
                    }

                    assertEquals(RoomState.WAITING, f.scenario.rooms.current(f.owner).orElseThrow().state());
                    assertEquals(1, f.scenario.rooms.forChannel(1).getFirst().playerCount());
                }
            }
        }
    }

    @Test
    void supportsUnequalTeamsWithoutMovingAnyoneToAnotherPhysicalSlot() {
        try (var f = new MatchRoomFixture(5, RoomMode.ITEM)) {
            for (int index : new int[]{2, 4}) {
                var id = f.players.get(index);
                f.scenario.rooms.setReady(id, false);
                f.scenario.rooms.changeTeam(id, RoomTeam.RED);
                f.scenario.rooms.setReady(id, true);
            }

            var result = f.scenario.rooms.reserveMatch(f.owner);
            var match = f.scenario.matches.current(result.token()).orElseThrow();

            assertEquals(RESERVED, result.outcome());
            assertEquals(1, match.participants().stream().filter(p -> p.team() == RoomTeam.BLUE).count());
            assertEquals(4, match.participants().stream().filter(p -> p.team() == RoomTeam.RED).count());
            assertEquals(List.of(0, 2, 3, 6, 7), match.participants().stream().map(p -> p.slotIndex()).toList());
        }
    }
}
