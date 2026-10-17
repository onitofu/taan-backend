package taan.online.lobby.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.malformed;
import static taan.online.RoomFixtures.root;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.lobby.protocol.packet.PcLobbyInitRoomList;
import taan.online.lobby.protocol.packet.PcLobbyUpdateRoomList;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.GameSessionInfoPack;

class LobbyRoomListCodecTest {
    @Test
    void matchesIndependentFullListAddCountAndDeletionFixtures() throws Exception {
        var codec = new LobbyRoomListCodec();
        var room = new GameSessionInfoPack(0x3ff, 1, 1, 0, "Room 1", 0, 0, 2, 1, 8, 1, null);
        var initial = new PcLobbyInitRoomList(List.of(room));
        var added = new PcLobbyUpdateRoomList(List.of(room));
        byte[] initBytes = root("lobby-init-one-room");
        byte[] addBytes = root("lobby-add-one-room");

        assertEquals(initial, codec.initialize(initBytes));
        assertArrayEquals(initBytes, codec.encode(initial));
        assertArrayEquals(initBytes, new LobbyResponseCodec().rooms(initial));
        assertEquals(added, codec.update(addBytes));
        assertArrayEquals(addBytes, codec.encode(added));
        malformed(codec::initialize, initBytes);
        malformed(codec::update, addBytes);

        var count = new PcLobbyUpdateRoomList(List.of(new GameSessionInfoPack(8, 7, 0, 0, "", 0, 0, 0, 2, 0, 0, null)));
        var deletion = new PcLobbyUpdateRoomList(
            List.of(new GameSessionInfoPack(0x8000, 7, 0, 0, "", 0, 0, 0, 0, 0, 0, null))
        );

        assertEquals(count, codec.update(root("lobby-room-count")));
        assertArrayEquals(root("lobby-room-count"), codec.encode(count));
        assertEquals(deletion, codec.update(root("lobby-room-delete")));
        assertArrayEquals(root("lobby-room-delete"), codec.encode(deletion));
        assertEquals(new PcLobbyInitRoomList(List.of()), codec.initialize(root("lobby-room-empty-snapshot")));
        assertArrayEquals(root("lobby-room-empty-snapshot"), codec.encode(new PcLobbyInitRoomList(List.of())));
        malformed(codec::update, root("lobby-room-count"));
        malformed(codec::update, root("lobby-room-delete"));
    }

    @Test
    void preservesTheActualMaskOrderWithDistinctValuesAndHiddenProtectedPasswords() throws Exception {
        var codec = new LobbyRoomListCodec();
        var distinct = new GameSessionInfoPack(0x3ff, 321, 1, 9, "Room 😀", 1, 0x1234, 3, 4, 8, 0x5678, "ключ");
        var expected = new PcLobbyInitRoomList(List.of(distinct));
        byte[] bytes = root("lobby-distinct-fields");

        assertEquals(expected, codec.initialize(bytes));
        assertArrayEquals(bytes, codec.encode(expected));
        assertFalse(distinct.toString().contains("ключ"));
        malformed(codec::initialize, bytes);

        for (String password : new String[]{null, "", "пароль"}) {
            var protectedRoom = new GameSessionInfoPack(0x20, 321, 0, 0, "", 0, 0, 0, 0, 0, 0, password);
            var message = new PcLobbyUpdateRoomList(List.of(protectedRoom));

            assertEquals(message, codec.update(codec.encode(message)));
        }

        var source = new ArrayList<>(List.of(distinct));
        var first = new PcLobbyInitRoomList(source);
        var second = new PcLobbyUpdateRoomList(source);
        source.clear();

        assertEquals(1, first.rooms().size());
        assertEquals(1, second.rooms().size());
        assertThrows(UnsupportedOperationException.class, () -> first.rooms().clear());
        assertThrows(UnsupportedOperationException.class, () -> second.rooms().clear());
    }

    @Test
    void rejectsImpossibleCountsUnknownMaskBitsForeignNestedTypesAndUnselectedPayload() throws Exception {
        var codec = new LobbyRoomListCodec();
        byte[] bytes = root("lobby-init-one-room");

        for (long count : new long[]{0, 2, 0xffffffffL}) {
            assertThrows(MalformedPacketException.class, () -> codec.initialize(integer(bytes, 4, count, 4)));
        }

        assertThrows(MalformedPacketException.class, () -> codec.initialize(integer(bytes, 8, 0, 4)));
        assertThrows(MalformedPacketException.class, () -> codec.initialize(integer(bytes, 12, 0x400, 2)));
        assertThrows(MalformedPacketException.class, () -> codec.initialize(integer(bytes, 12, 0, 2)));
        assertThrows(MalformedPacketException.class, () -> codec.initialize(integer(bytes, 18, 0xffffffffL, 4)));

        var invalid = new GameSessionInfoPack(0x400, 1, 0, 0, "", 0, 0, 0, 0, 0, 0, null);

        assertThrows(MalformedPacketException.class, () -> codec.encode(new PcLobbyInitRoomList(List.of(invalid))));
    }
}
