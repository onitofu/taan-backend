package taan.online.lobby.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import taan.online.RoomListingFixtures;
import taan.online.lobby.protocol.packet.PcLobbyInitRoomList;
import taan.online.lobby.view.LobbyRoomListMapper;
import taan.online.room.protocol.packet.GameSessionInfoPack;

class LobbyRoomListBatchCodecTest {
    @Test
    void matchesGoldenPacketsAndKeepsEmptyInitializationDistinctFromAnEmptyUpdate() throws Exception {
        var room = new GameSessionInfoPack(0x3ff, 1, 1, 0, "Room 1", 0, 0, 2, 1, 8, 1, null);
        var codec = new LobbyRoomListBatchCodec(16384);

        assertArrayEquals(root("lobby-init-one-room"), codec.initialize(List.of(room)).getFirst());
        assertArrayEquals(root("lobby-add-one-room"), codec.update(List.of(room)).getFirst());
        assertArrayEquals(root("lobby-room-empty-snapshot"), codec.initialize(List.of()).getFirst());
        assertTrue(codec.update(List.of()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new LobbyRoomListBatchCodec(7));
        assertArrayEquals(
            root("lobby-room-empty-snapshot"),
            new LobbyRoomListBatchCodec(8)
                .initialize(List.of()).getFirst()
        );
    }

    @Test
    void splitsWithoutDroppingOrDuplicatingEntriesAndHonorsExactFrameBoundaries() {
        var mapper = new LobbyRoomListMapper();
        var rooms = IntStream.rangeClosed(1, 128).mapToObj(
            id -> mapper.map(RoomListingFixtures.room(id, 1, "x".repeat(256)))
        ).toList();
        int entryBytes = new LobbyRoomListCodec().encode(new PcLobbyInitRoomList(rooms.subList(0, 1))).length - 8;

        assertEquals(LobbyRoomListBatchCodec.MAX_PUBLIC_ENTRY_BYTES, entryBytes);

        for (int frame : new int[]{entryBytes + 8, entryBytes * 2 + 8, entryBytes * 2 + 7, 16384}) {
            var codec = new LobbyRoomListBatchCodec(frame);
            var packets = codec.initialize(rooms);
            var restored = new ArrayList<GameSessionInfoPack>();
            var wire = new LobbyRoomListCodec();
            restored.addAll(wire.initialize(packets.getFirst()).rooms());
            packets.stream().skip(1).forEach(packet -> restored.addAll(wire.update(packet).rooms()));

            assertEquals(rooms, restored);
            assertTrue(packets.stream().allMatch(packet -> packet.length <= frame));
            var updates = new ArrayList<GameSessionInfoPack>();
            codec.update(rooms).forEach(packet -> updates.addAll(wire.update(packet).rooms()));

            assertEquals(rooms, updates);
            assertThrows(UnsupportedOperationException.class, () -> packets.clear());
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> new LobbyRoomListBatchCodec(entryBytes + 7).initialize(rooms)
        );
    }
}
