package taan.online.room.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static taan.online.RoomFixtures.appearance;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.malformed;
import static taan.online.RoomFixtures.occupied;
import static taan.online.RoomFixtures.offset;
import static taan.online.RoomFixtures.root;
import static taan.online.RoomFixtures.slots;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.character.protocol.packet.CharacterAppear;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.GameSessionDetails;
import taan.online.room.protocol.packet.GameSessionInfo;
import taan.online.room.protocol.packet.GameSessionOption;
import taan.online.room.protocol.packet.GameSlotInfo;
import taan.online.room.protocol.packet.GameSlotOccupant;
import taan.online.room.protocol.packet.PcGameSessionInit;
import taan.online.room.protocol.packet.PcGameSessionUpdateOption;
import taan.online.room.protocol.packet.PcGameSessionUpdateSlot;
import taan.online.room.protocol.packet.PrGameSessionCreateFail;
import taan.online.room.protocol.packet.PrGameSessionEnterFail;
import taan.online.room.protocol.packet.PrGameSessionLeave;

class RoomResponseCodecTest {
    @Test
    void matchesIndependentInitialRosterAppearanceAndBothIncrementalUpdates() throws Exception {
        var codec = new RoomResponseCodec();
        var expected = new PcGameSessionInit(
            new GameSessionInfo(1, 1, new GameSessionDetails(0, "Ngon vào đây ! ", 0, 0, 2, 2, 8, null, 1)),
            new GameSessionOption(1001, 1),
            slots(),
            0,
            "",
            ""
        );
        byte[] init = root("PcGameSession_Init-two-players");

        assertEquals(expected, codec.initialize(init));
        assertArrayEquals(init, codec.encode(expected));
        assertEquals(appearance(), codec.initialize(init).slots().get(2).occupant().appearance());
        assertThrows(UnsupportedOperationException.class, () -> expected.slots().clear());

        var joined = new PcGameSessionUpdateSlot(1, occupied(2, 2, 1002, "GuestHero", 2), "");
        var left = new PcGameSessionUpdateSlot(1, new GameSlotInfo(0, 0, null), "");
        var opened = new PcGameSessionUpdateSlot(1, new GameSlotInfo(2, 0, null), "");
        var option = new PcGameSessionUpdateOption(new GameSessionOption(1002, 2));

        assertArrayEquals(root("PcGameSession_UpdateSlot-join-red2"), codec.encode(joined));
        assertEquals(joined, codec.slot(root("PcGameSession_UpdateSlot-join-red2")));
        assertArrayEquals(root("PcGameSession_UpdateSlot-leave-blue0"), codec.encode(left));
        assertEquals(left, codec.slot(root("PcGameSession_UpdateSlot-leave-blue0")));
        assertArrayEquals(root("slot-open"), codec.encode(opened));
        assertEquals(opened, codec.slot(root("slot-open")));
        assertArrayEquals(root("owner-transfer"), codec.encode(option));
        assertEquals(option, codec.option(root("owner-transfer")));

        malformed(codec::initialize, init);
        malformed(codec::slot, root("PcGameSession_UpdateSlot-join-red2"));
        malformed(codec::slot, root("slot-open"));
        malformed(codec::option, root("owner-transfer"));
    }

    @Test
    void encodesFailurePacketsAndLeaveOutcomesWithoutInventingPositiveCreateOrEnterAcks() throws Exception {
        var codec = new RoomResponseCodec();
        byte[] failed = root("create-fail-generic");

        assertEquals(new PrGameSessionCreateFail(0), codec.createFail(failed));
        assertArrayEquals(failed, codec.encode(new PrGameSessionCreateFail(0)));
        malformed(codec::createFail, failed);

        for (int reason = 3; reason <= 8; reason++) {
            byte[] bytes = root("enter-fail-" + reason);

            assertEquals(new PrGameSessionEnterFail(reason), codec.enterFail(bytes));
            assertArrayEquals(bytes, codec.encode(new PrGameSessionEnterFail(reason)));
            malformed(codec::enterFail, bytes);
        }

        for (boolean accepted : new boolean[]{false, true}) {
            byte[] bytes = root(accepted ? "leave-success" : "leave-rejected");
            var response = new PrGameSessionLeave(accepted ? 1 : 0);

            assertEquals(response, codec.leave(bytes));
            assertArrayEquals(bytes, codec.encode(response));
            malformed(codec::leave, bytes);
        }
    }

    @Test
    void rejectsIncorrectCountsOrderingNestedTypesAndUnsupportedOptionalObjects() throws Exception {
        var codec = new RoomResponseCodec();
        byte[] init = root("PcGameSession_Init-two-players");
        int optionOffset = offset(init, GameSessionOption.TYPE_ID);
        int countOffset = optionOffset + 12;
        int slotOffset = countOffset + 4;
        int appearanceOffset = offset(init, CharacterAppear.TYPE_ID);

        assertThrows(MalformedPacketException.class, () -> codec.initialize(integer(init, 4, 0, 4)));
        assertThrows(MalformedPacketException.class, () -> codec.initialize(integer(init, optionOffset, 0, 4)));
        assertThrows(MalformedPacketException.class, () -> codec.initialize(integer(init, slotOffset, 0, 4)));
        assertThrows(MalformedPacketException.class, () -> codec.initialize(integer(init, appearanceOffset, 0, 4)));
        assertThrows(
            MalformedPacketException.class,
            () -> codec.initialize(integer(init, appearanceOffset + 171, 1, 1))
        );
        assertThrows(
            MalformedPacketException.class,
            () -> codec.initialize(integer(init, appearanceOffset + 174, 1, 1))
        );
        assertThrows(MalformedPacketException.class, () -> codec.initialize(integer(init, slotOffset + 4, 1, 1)));

        for (long count : new long[]{0, 1, 7, 9, 0xffffffffL}) {
            assertThrows(MalformedPacketException.class, () -> codec.initialize(integer(init, countOffset, count, 4)));
        }

        var parsed = codec.initialize(init);
        var reversed = new ArrayList<>(parsed.slots());
        Collections.swap(reversed, 0, 2);
        var duplicate = new ArrayList<>(parsed.slots());
        duplicate.set(1, duplicate.getFirst());

        for (List<GameSlotInfo> invalid : List.of(List.<GameSlotInfo>of(), reversed, duplicate)) {
            var message = new PcGameSessionInit(parsed.info(), parsed.option(), invalid, 0, "", "");

            assertThrows(MalformedPacketException.class, () -> codec.encode(message));
        }
    }

    @Test
    void preservesReadyClosedAndAppearanceAbsentSlotsAndRejectsInvalidSlotShapes() {
        var codec = new RoomResponseCodec();
        var person = new GameSlotOccupant(0xffffffffL, 1009, "Hero 😀", "Guild", 65535, 2, null, 65535);

        for (GameSlotInfo slot : List.of(new GameSlotInfo(7, 1, null), new GameSlotInfo(3, 3, person))) {
            var response = new PcGameSessionUpdateSlot(0, slot, "Side 😀");

            assertEquals(response, codec.slot(codec.encode(response)));
        }

        for (GameSlotInfo slot : List.of(
            new GameSlotInfo(-1, 0, null),
            new GameSlotInfo(8, 0, null),
            new GameSlotInfo(0, -1, null),
            new GameSlotInfo(0, 4, null),
            new GameSlotInfo(0, 0, person),
            new GameSlotInfo(0, 2, null)
        )) {
            assertThrows(MalformedPacketException.class, () -> codec.encode(new PcGameSessionUpdateSlot(1, slot, "")));
        }

        var writer = new BinaryWriter().unsignedInt(PcGameSessionUpdateSlot.TYPE_ID).unsignedByte(1)
            .unsignedInt(GameSlotInfo.TYPE_ID).unsignedByte(8).unsignedByte(0).wideString("");

        assertThrows(MalformedPacketException.class, () -> codec.slot(writer.toByteArray()));
    }
}
