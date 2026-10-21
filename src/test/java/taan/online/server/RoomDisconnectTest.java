package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.drain;
import static taan.online.server.RoomConnectionWorld.read;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RoomFixtures;
import taan.online.SelectionFixtures;
import taan.online.TestFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.network.FrameAssemblyTimeoutEvent;
import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.session.service.model.LoginRejected;

class RoomDisconnectTest {
    @Test
    void handsOffOnIdleTransportFailureAndOrdinaryDisconnectWithoutLeavingTheSurvivor() throws Exception {
        for (String mode : List.of("EOF", "IDLE", "WRITE")) {
            try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
                var owner = world.client(1);
                var guest = world.client(2);
                drain(owner);
                int room = world.create(owner);
                drain(guest);
                world.join(guest, room);
                drain(owner);

                if (mode.equals("IDLE")) {
                    owner.channel().advanceTimeBy(301, TimeUnit.SECONDS);
                    owner.channel().runScheduledPendingTasks();
                } else if (mode.equals("WRITE")) {
                    var delayed = new QueuedOutboundWrites();
                    owner.channel().pipeline().addBefore(owner.context().name(), "delay", delayed);
                    world.request(owner, RoomFixtures.root("start-empty"));
                    delayed.fail();
                } else {
                    owner.disconnect(false);
                }

                world.tick();
                assertArrayEquals(RoomFixtures.root("PcGameSession_UpdateSlot-leave-blue0"), read(guest));
                assertArrayEquals(RoomFixtures.root("owner-transfer"), read(guest));
                assertFalse(owner.channel().isActive());
                assertTrue(guest.channel().isActive());
                assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
                assertEquals(1, world.channels.catalog().fills().get(1));
                world.request(guest, SelectionFixtures.cash(0, 0));
                assertArrayEquals(SelectionFixtures.cash(0, 0), read(guest));
                assertArrayEquals(SelectionFixtures.money(0), read(guest));
                assertNull(guest.channel().readOutbound());
                assertEquals(1, count(logs, "room.owner.changed"));
                assertEquals(1, count(logs, "room.left"));
                assertEquals(1, count(logs, "session.ended"));
                assertFalse(logs.text().contains("Private transport message"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            }
        }
    }

    @Test
    void preservesTheFirstCloseReasonAndIgnoresRepeatedInactiveEvents() throws Exception {
        try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            var gate = new DelayedChannelClose();
            owner.channel().pipeline().addBefore(owner.context().name(), "closing", gate);
            owner.handler().exceptionCaught(owner.context(), new IOException("Private socket value"));
            owner.handler().userEventTriggered(owner.context(), FrameAssemblyTimeoutEvent.INSTANCE);
            owner.channel().advanceTimeBy(301, TimeUnit.SECONDS);
            owner.channel().runScheduledPendingTasks();
            world.tick();

            assertEquals(1, count(logs, "room.owner.changed"));
            assertEquals(0, count(logs, "connection.closed"));
            gate.finish();
            owner.handler().channelInactive(owner.context());
            owner.handler().userEventTriggered(owner.context(), FrameAssemblyTimeoutEvent.INSTANCE);
            world.tick();

            assertEquals(1, count(logs, "connection.closed"));
            assertEquals(1, count(logs, "session.ended"));
            assertEquals(1, count(logs, "room.owner.changed"));
            assertTrue(
                logs.events().stream().filter(
                    event -> event.getKeyValuePairs().stream().anyMatch(
                        field -> field.key.equals("event") && field.value.equals("connection.closed")
                    )
                ).allMatch(
                    event -> event.getKeyValuePairs().stream().anyMatch(
                        field -> field.key.equals("reason") && field.value.equals("TRANSPORT_CLOSED")
                    )
                )
            );
            assertFalse(logs.text().contains("Private socket value"));
        }
    }

    @Test
    void ignoresLateWritesOfTheOldConnectionAfterTheSameAccountHasRejoined() throws Exception {
        for (boolean failure : new boolean[]{true, false}) {
            try (var world = new RoomConnectionWorld()) {
                var old = world.client(1);
                var guest = world.client(2);
                drain(old);
                int room = world.create(old);
                drain(guest);
                world.join(guest, room);
                drain(old);
                var delayed = new QueuedOutboundWrites();
                old.channel().pipeline().addBefore(old.context().name(), "delay", delayed);
                world.request(old, RoomFixtures.root("start-empty"));
                old.disconnect(false);
                world.tick();
                read(guest);
                read(guest);
                var current = world.client(1);
                drain(guest);
                world.join(current, room);
                read(guest);

                if (failure) {
                    delayed.fail();
                } else {
                    delayed.succeed();
                }

                old.handler().channelInactive(old.context());
                world.tick();
                assertTrue(current.channel().isActive());
                assertTrue(guest.channel().isActive());
                assertEquals(2, world.rooms.forChannel(1).getFirst().playerCount());
                assertEquals(2, world.channels.catalog().fills().get(1));
                var claimant = world.sessions.open();
                assertInstanceOf(
                    LoginRejected.class,
                    world.sessions.tryClaim(
                        claimant,
                        new AuthenticatedPrincipal(new AccountId(1), AccountGender.FEMALE)
                    )
                );
                world.sessions.close(claimant);
                world.request(current, SelectionFixtures.cash(0, 0));
                assertArrayEquals(SelectionFixtures.cash(0, 0), read(current));
                assertArrayEquals(SelectionFixtures.money(0), read(current));
                assertNull(guest.channel().readOutbound());
            }
        }
    }

    private long count(LogCapture logs, String name) {
        return logs.events().stream().filter(
            event -> event.getKeyValuePairs().stream().anyMatch(
                field -> field.key.equals("event") && field.value.equals(name)
            )
        ).count();
    }
}
