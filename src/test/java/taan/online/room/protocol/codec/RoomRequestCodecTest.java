package taan.online.room.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.malformed;
import static taan.online.RoomFixtures.reader;
import static taan.online.RoomFixtures.root;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.GameSessionDetails;
import taan.online.room.protocol.packet.GameSessionInfo;
import taan.online.room.protocol.packet.PeerMeta;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.protocol.packet.PqGameSessionLeave;

class RoomRequestCodecTest {
    @Test
    void readsIndependentCreateEnterAndLeaveRequestsWithoutInventingPeerMeaning() throws Exception {
        var codec = new RoomRequestCodec();
        var zeroPeer = new PeerMeta(0, 0, 0, 0, 0, 0);
        var plain = codec.create(root("create-unprotected"));
        var protectedRoom = codec.create(root("create-password"));

        assertEquals(7, plain.info().roomId());
        assertEquals(5, plain.info().state());
        assertEquals("Комната", plain.info().details().title());
        assertEquals(0, plain.info().details().roomGrade());
        assertEquals(0, plain.info().details().gameKind());
        assertEquals(0, plain.info().details().gameType());
        assertEquals(2, plain.info().details().rule());
        assertEquals(0, plain.info().details().playerCount());
        assertEquals(8, plain.info().details().capacity());
        assertEquals(1, plain.info().details().mapId());
        assertNull(plain.info().details().password());
        assertEquals(zeroPeer, plain.peerMeta());
        assertEquals("пароль", protectedRoom.info().details().password());
        assertEquals(new PqGameSessionEnter(7, "пароль", zeroPeer), codec.enter(root("enter-password")));
        assertEquals(new PqGameSessionEnter(7, "", zeroPeer), codec.enter(root("enter-empty-password")));
        assertEquals(new PqGameSessionLeave(7, 0), codec.leave(root("leave-request")));

        malformed(codec::create, root("create-password"));
        malformed(codec::create, root("create-unprotected"));
        malformed(codec::enter, root("enter-password"));
        malformed(codec::enter, root("enter-empty-password"));
        malformed(codec::leave, root("leave-request"));
    }

    @Test
    void rejectsInvalidNestedTypesAndCountedUtf16WithoutLeakingPasswords() throws Exception {
        var codec = new RoomRequestCodec();
        byte[] create = root("create-password");
        byte[] enter = root("enter-password");

        var failure = assertThrows(MalformedPacketException.class, () -> codec.create(integer(create, 4, 0, 4)));
        assertFalse(failure.toString().contains("пароль"));
        assertThrows(MalformedPacketException.class, () -> codec.create(integer(create, 12, 0xffffffffL, 4)));
        assertThrows(MalformedPacketException.class, () -> codec.enter(integer(enter, 6, 0xffffffffL, 4)));
        assertThrows(MalformedPacketException.class, () -> codec.enter(integer(enter, 10, 0xd800, 2)));
        assertFalse(codec.create(create).toString().contains("пароль"));
        assertFalse(codec.create(create).info().toString().contains("пароль"));
        assertFalse(codec.create(create).info().details().toString().contains("пароль"));
        assertFalse(codec.enter(enter).toString().contains("пароль"));

        var peer = new PeerMeta(0xffffffffL, 0x80000000L, 255, 0x12345678, 3, 4);
        var writer = new BinaryWriter();
        new PeerMetaCodec().encode(writer, peer);
        byte[] encoded = writer.toByteArray();

        assertEquals(21, encoded.length);
        assertArrayEquals(HexFormat.of().parseHex("ffffffff00000080ff785634120300000004000000"), encoded);
        assertEquals(peer, new PeerMetaCodec().decode(reader(encoded)));
        var request = new BinaryWriter().unsignedInt(PqGameSessionEnter.TYPE_ID).unsignedShort(7).wideString("😀");
        request.bytes(encoded);

        assertEquals(new PqGameSessionEnter(7, "😀", peer), codec.enter(request.toByteArray()));
    }

    @Test
    void preservesTheShortStateZeroInfoAndExplicitPasswordPresence() {
        var codec = new GameSessionInfoCodec();
        var empty = new GameSessionInfo(9, 0, null);
        var output = new BinaryWriter();
        codec.encode(output, empty);
        var input = reader(output.toByteArray());

        assertEquals(7, output.toByteArray().length);
        assertEquals(empty, codec.decode(input));
        input.finish();

        for (String password : new String[]{null, "", "пароль"}) {
            var details = new GameSessionDetails(1, "Room 😀", 1, 0x1234, 3, 2, 8, password, 0x5678);
            var info = new GameSessionInfo(9, 5, details);
            var writer = new BinaryWriter();
            codec.encode(writer, info);
            var data = reader(writer.toByteArray());

            assertEquals(info, codec.decode(data));
            data.finish();
            assertThrows(
                MalformedPacketException.class,
                () -> codec.encode(new BinaryWriter(), new GameSessionInfo(9, 0, details))
            );
        }

        assertThrows(
            MalformedPacketException.class,
            () -> codec.encode(new BinaryWriter(), new GameSessionInfo(9, 1, null))
        );
    }
}
