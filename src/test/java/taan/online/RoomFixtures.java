package taan.online;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import taan.online.character.protocol.packet.CharacterAppear;
import taan.online.character.protocol.packet.CharacterPart;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.GameSlotInfo;
import taan.online.room.protocol.packet.GameSlotOccupant;

public final class RoomFixtures {
    private RoomFixtures() {
    }

    public static byte[] root(String name) throws Exception {
        var resource = RoomFixtures.class.getResource("/room/" + name + ".frame.hex");
        byte[] frame = HexFormat.of().parseHex(Files.readString(Path.of(resource.toURI())).replaceAll("\\s", ""));
        return Arrays.copyOfRange(frame, 4, frame.length);
    }

    public static BinaryReader reader(byte[] typed) {
        return new BinaryReader(new BinaryWriter().unsignedInt(0).bytes(typed).toByteArray(), 0);
    }

    public static byte[] integer(byte[] bytes, int offset, long value, int width) {
        byte[] changed = bytes.clone();

        for (int index = 0; index < width; index++) {
            changed[offset + index] = (byte) (value >>> (index * 8));
        }

        return changed;
    }

    public static void malformed(Function<byte[], ?> decoder, byte[] valid) {
        for (int length = 0; length < valid.length; length++) {
            byte[] truncated = Arrays.copyOf(valid, length);
            assertThrows(MalformedPacketException.class, () -> decoder.apply(truncated));
        }

        assertThrows(MalformedPacketException.class, () -> decoder.apply(Arrays.copyOf(valid, valid.length + 1)));
        assertThrows(MalformedPacketException.class, () -> decoder.apply(integer(valid, 0, 0, 4)));
    }

    public static CharacterAppear appearance() {
        var parts = new ArrayList<CharacterPart>();

        for (int index = 0; index < 16; index++) {
            parts.add(new CharacterPart(0, 0, 0, 0));
        }

        parts.set(1, new CharacterPart(729, 288, 288, 288));
        parts.set(3, new CharacterPart(823, 262, 262, 262));
        parts.set(5, new CharacterPart(725, 266, 311, 279));
        parts.set(8, new CharacterPart(736, 276, 276, 276));
        return new CharacterAppear(1, 0, 0, parts);
    }

    public static List<GameSlotInfo> slots() {
        var slots = new ArrayList<GameSlotInfo>();

        for (int index = 0; index < 8; index++) {
            slots.add(new GameSlotInfo(index, 0, null));
        }

        slots.set(0, occupied(0, 1, 1001, "ProbeHero", 1));
        slots.set(2, occupied(2, 2, 1002, "GuestHero", 2));
        return slots;
    }

    public static GameSlotInfo occupied(int slot, long account, long character, String name, int team) {
        return new GameSlotInfo(slot, 2, new GameSlotOccupant(account, character, name, "", 1, team, appearance(), 3));
    }

    public static int offset(byte[] bytes, int type) {
        byte[] needle = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(type).array();

        for (int index = 4; index <= bytes.length - 4; index++) {
            if (Arrays.equals(bytes, index, index + 4, needle, 0, 4)) {
                return index;
            }
        }

        throw new IllegalArgumentException("Missing fixture type");
    }
}
