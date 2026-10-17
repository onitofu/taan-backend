package taan.online.lobby.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.LogCapture;
import taan.online.RoomListingFixtures;
import taan.online.lobby.protocol.codec.LobbyRoomListCodec;
import taan.online.lobby.service.model.LobbyRoomListSnapshot;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.RoomListing;

class LobbyRoomListingViewTest {
    @Test
    void mapsBothModesAndActualCountsWithoutPrivateRoomData() {
        var source = RoomListingFixtures.room(1, 2, "Visible title");
        var mapper = new LobbyRoomListMapper();
        var item = mapper.map(source);
        var noItem = mapper.map(
            new RoomListing(
                source.id(),
                2,
                source.title(),
                RoomState.WAITING,
                RoomMode.NO_ITEM,
                RoomRule.BASIC,
                2,
                8,
                1,
                false
            )
        );

        assertEquals(0x3ff, item.mask());
        assertEquals(1, item.state());
        assertEquals(0, item.gameType());
        assertEquals(2, item.rule());
        assertEquals(1, noItem.gameType());
        assertEquals(0, noItem.rule());
        assertEquals(2, noItem.playerCount());
        assertNull(item.password());
        assertNull(noItem.password());
        assertThrows(
            IllegalArgumentException.class,
            () -> mapper.map(
                new RoomListing(
                    source.id(),
                    2,
                    source.title(),
                    RoomState.CLOSED,
                    source.mode(),
                    source.rule(),
                    0,
                    8,
                    1,
                    false
                )
            )
        );
    }

    @Test
    void queuesFreshInitialUpsertAndReplacementPacketsInOrderAndOmitsTitlesFromLogs() {
        var id = UUID.randomUUID();
        var first = RoomListingFixtures.room(1, 2, "Private room title");
        var second = RoomListingFixtures.room(2, 2, "Other room");
        var current = new AtomicReference<>(new LobbyRoomListSnapshot(2, List.of(first, second)));
        var out = new CollectingPacketOutput();
        var view = new ProtocolLobbyView(id, ignored -> Optional.of(current.get()), out, 16384);
        var wire = new LobbyRoomListCodec();

        try (var logs = new LogCapture()) {
            view.initialize(0, List.of()).toCompletableFuture().join();

            assertEquals(
                List.of(new LobbyRoomListMapper().map(first), new LobbyRoomListMapper().map(second)),
                wire.initialize(out.batches.getFirst().get(1)).rooms()
            );
            view.roomChanged(2).toCompletableFuture().join();

            assertEquals(
                List.of(new LobbyRoomListMapper().map(second)),
                wire.update(out.batches.getLast().getFirst()).rooms()
            );
            current.set(new LobbyRoomListSnapshot(2, List.of(second)));
            view.roomsReplaced().toCompletableFuture().join();

            assertEquals(
                List.of(new LobbyRoomListMapper().map(second)),
                wire.initialize(out.batches.getLast().getFirst()).rooms()
            );
            view.roomChanged(99).toCompletableFuture().join();

            assertEquals(3, out.batches.size());
            assertTrue(logs.text().contains("lobby.room_list.queued"));
            assertTrue(logs.text().contains("recipientSessionId"));
            assertFalse(logs.text().contains("Private room title"));
        }

        view.close("TEST");

        assertEquals(List.of("TEST"), out.closeReasons);
    }

    @Test
    void doesNotDeliverToAStaleSubscriptionAndCopiesCallScopedSnapshots() {
        var out = new CollectingPacketOutput();
        var view = new ProtocolLobbyView(UUID.randomUUID(), ignored -> Optional.empty(), out, 16384);
        view.initialize(0, List.of()).toCompletableFuture().join();
        view.roomChanged(1).toCompletableFuture().join();
        view.roomsReplaced().toCompletableFuture().join();

        assertTrue(out.batches.isEmpty());
        var source = new ArrayList<>(List.of(RoomListingFixtures.room(1, 1, "Room")));
        var snapshot = new LobbyRoomListSnapshot(1, source);
        source.clear();

        assertEquals(1, snapshot.rooms().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.rooms().clear());
    }
}
