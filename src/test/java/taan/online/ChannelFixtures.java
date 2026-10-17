package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import taan.online.lobby.protocol.packet.UserSimpleInfo;

public final class ChannelFixtures {
    private ChannelFixtures() {
    }

    public static byte[] fixture(String name) throws Exception {
        String suffix = name.endsWith(".packet") ? ".hex" : ".root.hex";

        try (var input = ChannelFixtures.class.getResourceAsStream("/channels/" + name + suffix)) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll(
                    "\\s+",
                    ""
                )
            );
        }
    }

    public static byte[] move(int channel) {
        return move(channel, 0, 0, 929);
    }

    public static byte[] move(int channel, int mode, long follow, long revision) {
        return ByteBuffer.allocate(15).order(ByteOrder.LITTLE_ENDIAN).putInt(0x33c00633).put((byte) mode)
            .putShort((short) channel).putInt((int) follow).putInt((int) revision).array();
    }

    public static byte[] enter(int first) {
        return ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).putInt(0x22aa0516).put((byte) first)
            .put((byte) 255).putInt(-1).array();
    }

    public static byte[] leave() {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x22850505).array();
    }

    public static int type(byte[] root) {
        return ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public static List<Long> users(byte[] root) {
        return roster(root).stream().map(UserSimpleInfo::characterId).toList();
    }

    public static List<UserSimpleInfo> roster(byte[] root) {
        var input = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN);
        input.getInt();
        return users(input);
    }

    public static List<Long> removed(byte[] root) {
        var input = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x612308a8, input.getInt());
        users(input);
        int count = input.getInt();
        var ids = new ArrayList<Long>();

        for (int index = 0; index < count; index++) {
            ids.add(Integer.toUnsignedLong(input.getInt()));
        }

        assertEquals(0, input.getInt());
        assertEquals(0, input.remaining());
        return ids;
    }

    private static List<UserSimpleInfo> users(ByteBuffer input) {
        int count = input.getInt();
        var users = new ArrayList<UserSimpleInfo>();

        for (int index = 0; index < count; index++) {
            assertEquals(0x29b00595, input.getInt());
            long id = Integer.toUnsignedLong(input.getInt());
            int length = input.getInt();
            var name = new byte[length * 2];
            input.get(name);
            int level = Short.toUnsignedInt(input.getShort());
            int genderIcon = Byte.toUnsignedInt(input.get());
            users.add(new UserSimpleInfo(id, new String(name, StandardCharsets.UTF_16LE), level, genderIcon));
        }

        return users;
    }
}
