package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.CollectingPacketOutput;
import taan.online.RecordingRoomOutput;
import taan.online.RoomListingFixtures;
import taan.online.RoomScenario;
import taan.online.account.model.AccountGender;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.service.model.ChannelMembershipSnapshot;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.room.model.RoomIdPool;
import taan.online.room.model.RoomParticipant;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.room.service.model.LeaveRoomOutcome;
import taan.online.room.service.model.RoomMembership;
import taan.online.session.model.SessionId;

class RoomRegistryTest {
    @Test
    void bindsRoomsToTheirChannelAndSessionAndMakesLeaveAndCleanupIdempotent() {
        try (var scenario = new RoomScenario()) {
            var a = scenario.player(1, 1);
            var b = scenario.player(2, 2);
            var first = scenario.create.execute(a, RoomScenario.command(), new RecordingRoomOutput()).room();
            var token = scenario.rooms.membership(a).orElseThrow();
            var second = scenario.create.execute(b, RoomScenario.command(), new RecordingRoomOutput()).room();

            assertNotEquals(first.id(), second.id());
            assertEquals(1, first.channelId());
            assertEquals(2, second.channelId());
            assertEquals(LeaveRoomOutcome.REJECTED, scenario.leave.execute(b, first.id().value(), 0).outcome());
            assertEquals(LeaveRoomOutcome.REJECTED, scenario.leave.execute(a, first.id().value(), 1).outcome());
            assertEquals(LeaveRoomOutcome.LEFT, scenario.leave.execute(a, first.id().value(), 0).outcome());
            assertTrue(scenario.rooms.current(a).isEmpty());
            assertEquals(LeaveRoomOutcome.REJECTED, scenario.leave.execute(a, 999, 0).outcome());
            assertEquals(LeaveRoomOutcome.REPLAYED, scenario.leave.execute(a, first.id().value(), 0).outcome());
            scenario.channels.leave(
                a,
                RoomListingFixtures.view(a, scenario.channels, scenario.rooms, new CollectingPacketOutput())
            );
            scenario.lobby(a);
            var next = scenario.create.execute(a, RoomScenario.command(), new RecordingRoomOutput()).room();

            assertNotEquals(first.id(), next.id());
            assertEquals(LeaveRoomOutcome.REJECTED, scenario.leave.execute(a, first.id().value(), 0).outcome());
            scenario.rooms.release(token, "LATE_CLOSE");
            scenario.rooms.release(
                new RoomMembership(a, next.id(), UUID.randomUUID(), next.channelId(), UUID.randomUUID()),
                "STALE_GENERATION"
            );

            assertEquals(next, scenario.rooms.current(a).orElseThrow());
            scenario.rooms.release(a, "DISCONNECT");
            scenario.rooms.release(a, "DISCONNECT");

            assertTrue(scenario.rooms.current(a).isEmpty());
            assertEquals(second, scenario.rooms.current(b).orElseThrow());
            assertEquals(LeaveRoomOutcome.REJECTED, scenario.leave.execute(a, first.id().value(), 0).outcome());
            scenario.rooms.close();
            scenario.rooms.close();

            assertTrue(scenario.rooms.current(b).isEmpty());
            assertEquals(
                CreateRoomOutcome.IGNORED,
                scenario.rooms.create(
                    scenario.channels.membership(a).orElseThrow(),
                    RoomScenario.settings(),
                    scenario.owner(a),
                    new RecordingRoomOutput()
                ).outcome()
            );
        }
    }

    @Test
    void rechecksTheChannelAndActorAndRefusesExhaustedIdsWithoutChangingMembership() {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            var context = scenario.channels.membership(id).orElseThrow();
            var owner = scenario.owner(id);
            var settings = RoomScenario.settings();
            var actor = context.participant();

            assertEquals(
                "CHANNEL_CHANGED",
                scenario.rooms.create(
                    new ChannelMembershipSnapshot(1, ChannelPresencePhase.MEMBER, actor),
                    settings,
                    owner,
                    new RecordingRoomOutput()
                ).reason()
            );
            assertEquals(
                "CHANNEL_CHANGED",
                scenario.rooms.create(
                    new ChannelMembershipSnapshot(2, ChannelPresencePhase.LOBBY, actor),
                    settings,
                    owner,
                    new RecordingRoomOutput()
                ).reason()
            );

            var other = scenario.player(2, 2);
            assertEquals(
                "CHARACTER_CHANGED",
                scenario.rooms.create(
                    context,
                    settings,
                    new RoomParticipant(id, scenario.owner(other).character(), 3),
                    new RecordingRoomOutput()
                ).reason()
            );
            var changed = CharacterFixtures.character(99, actor.accountId(), AccountGender.FEMALE, "OtherHero");
            assertEquals(
                "CHARACTER_CHANGED",
                scenario.rooms.create(
                    context,
                    settings,
                    new RoomParticipant(id, changed, 3),
                    new RecordingRoomOutput()
                ).reason()
            );
            scenario.rooms.create(context, settings, owner, new RecordingRoomOutput());

            assertEquals(
                "ALREADY_IN_ROOM",
                scenario.rooms.create(context, settings, owner, new RecordingRoomOutput()).reason()
            );
            scenario.channels.move(id, new MoveToCommand(0, 2, 0, 929));

            assertEquals(LeaveRoomOutcome.REJECTED, scenario.leave.execute(id, 1, 0).outcome());
            scenario.sessions.close(new SessionId(id));

            assertTrue(scenario.channels.membership(id).isEmpty());
            assertEquals(LeaveRoomOutcome.IGNORED, scenario.leave.execute(id, 1, 0).outcome());
            assertTrue(scenario.rooms.current(id).isEmpty());

            var pool = new RoomIdPool();
            for (int index = 1; index <= 65535; index++) {
                pool.allocate();
            }
            try (var exhausted = new RoomRegistry(scenario.channels, pool)) {
                var current = scenario.channels.membership(other).orElseThrow();
                assertEquals(
                    "ROOM_IDS_EXHAUSTED",
                    exhausted.create(current, settings, scenario.owner(other), new RecordingRoomOutput()).reason()
                );
                assertTrue(exhausted.membership(other).isEmpty());
            }
        }
    }

    @Test
    void serializesCompetingCreatesForOneSession() throws Exception {
        try (var scenario = new RoomScenario(); var workers = Executors.newFixedThreadPool(2)) {
            var id = scenario.player(1, 1);
            var barrier = new CyclicBarrier(2);
            var first = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput());
                }
            );
            var second = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput());
                }
            );
            var a = first.get(5, TimeUnit.SECONDS);
            var b = second.get(5, TimeUnit.SECONDS);

            assertNotEquals(a.outcome(), b.outcome());
            assertEquals(1, scenario.rooms.current(id).orElseThrow().id().value());
        }
    }
}
