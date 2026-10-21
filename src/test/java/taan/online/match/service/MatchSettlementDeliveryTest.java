package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RoomScenario;
import taan.online.match.repository.MatchSettlement;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchTerminationMode;

class MatchSettlementDeliveryTest {
    @Test
    void waitsForCommitBeforePublishingTheMedalAndReleasingTheRoomReturnBarrier() throws Exception {
        var work = new ArrayDeque<Runnable>();
        var writes = new AtomicInteger();
        var action = new SettleMatchResultAction(
            result -> {
                writes.incrementAndGet();
                return new MatchSettlement(result, true);
            },
            work::addLast,
            Clock.systemUTC()
        );
        var scenario = new RoomScenario(MatchExecutionMode.CLIENT_SCENARIO, action);

        try (var fixture = new MatchOutcomeFixture(scenario)) {
            var shooter = fixture.active();
            var other = fixture.room.players.stream().filter(id -> !id.equals(shooter)).findFirst().orElseThrow();
            scenario.matchTimers.advanceSeconds(1);
            fixture.shot(77, false, false);
            fixture.ack(shooter);
            fixture.ack(other);
            var terminated = fixture.surrender(other).toCompletableFuture().join();

            assertFalse(terminated.delivery().toCompletableFuture().isDone());
            assertEquals(0, writes.get());
            assertEquals(0, fixture.owner.ends);
            assertEquals(0, fixture.guest.ends);
            assertEquals(RoomState.MATCH, scenario.rooms.current(fixture.room.owner).orElseThrow().state());
            assertEquals(1, work.size());

            fixture.owner.onEnd = () -> {
                assertEquals(1, writes.get());
                assertEquals(RoomState.MATCH, scenario.rooms.current(fixture.room.owner).orElseThrow().state());
            };
            work.removeFirst().run();
            terminated.delivery().toCompletableFuture().join();

            assertEquals(1, fixture.owner.ends);
            assertEquals(1, fixture.guest.ends);
            assertTrue(fixture.owner.outcomes.getFirst().awardsGranted());
            assertEquals(
                77,
                fixture.owner.results.getFirst().result().participants().stream()
                .mapToLong(value -> value.damage()).sum()
            );
            assertEquals(RoomState.WAITING, scenario.rooms.current(fixture.room.owner).orElseThrow().state());
            fixture.matches.dispatchTermination(terminated.termination(), other, () -> writes.incrementAndGet())
                .toCompletableFuture().join();

            assertEquals(1, writes.get());
            assertEquals(1, fixture.owner.ends);
            assertTrue(scenario.matchTimers.deadlines.isEmpty());
        }
    }

    @Test
    void closesRecipientsAndDepartingPlayersOnFailureWithoutSendingAnUncommittedResult() throws Exception {
        for (boolean leave : new boolean[]{false, true}) {
            var work = new ArrayDeque<Runnable>();
            var action = new SettleMatchResultAction(
                result -> {
                    throw new IllegalStateException("private-settlement-value");
                },
                work::addLast,
                Clock.systemUTC()
            );
            var scenario = new RoomScenario(MatchExecutionMode.CLIENT_SCENARIO, action);

            try (var fixture = new MatchOutcomeFixture(scenario); var logs = new LogCapture()) {
                if (leave) {
                    scenario.leave.execute(fixture.room.owner, fixture.token.roomId().value(), 0);
                } else {
                    fixture.surrender(fixture.room.owner);
                }

                assertEquals(0, fixture.guest.ends);
                assertEquals(1, work.size());
                work.removeFirst().run();

                assertEquals(0, fixture.owner.ends);
                assertEquals(0, fixture.guest.ends);
                assertEquals(List.of(leave ? "MATCH_ROOM_LEAVE" : "MATCH_SETTLEMENT_FAILED"), fixture.owner.closed);
                assertEquals(List.of("MATCH_SETTLEMENT_FAILED"), fixture.guest.closed);
                assertTrue(fixture.matches.current(fixture.token).isEmpty());
                assertEquals(
                    RoomState.WAITING,
                    scenario.rooms.current(fixture.room.players.get(1))
                    .orElseThrow().state()
                );
                assertFalse(logs.text().contains("private-settlement-value"));
                assertFalse(logs.text().contains("match.rewards.committed"));
                assertEquals(
                    1,
                    logs.events().stream().filter(event -> event.getFormattedMessage()
                    .equals("Match rewards could not be committed")).count()
                );
            }
        }
    }

    @Test
    void closesAnExplicitDepartingSubscriptionWhenSettlementCannotBePublished() {
        try (var fixture = new MatchRuntimeFixture(8)) {
            var terminal = new RoomMatchTermination(
                fixture.token,
                RoomMatchEndReason.ROOM_LEAVE,
                RoomMatchTerminationMode.RETURN_TO_ROOM,
                List.of()
            );
            var delivery = new MatchTerminationDelivery(
                terminal,
                List.of(new MatchSubscription(fixture.room.players.get(1), fixture.guest)),
                new MatchSubscription(fixture.room.owner, fixture.owner),
                fixture.runtime.snapshot().participants(),
                null
            );
            delivery.settlementFailed();

            assertEquals(List.of("MATCH_SETTLEMENT_FAILED"), fixture.owner.closed);
            assertEquals(List.of("MATCH_SETTLEMENT_FAILED"), fixture.guest.closed);
            assertEquals(0, fixture.owner.ends);
            assertEquals(0, fixture.guest.ends);
        }
    }
}
