package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import taan.online.account.model.AccountGender;
import taan.online.channel.controller.GetChannelListPacketHandler;
import taan.online.channel.model.ChannelDefinition;
import taan.online.channel.model.ChannelDirectory;
import taan.online.channel.service.GetChannelListAction;
import taan.online.character.controller.SelectCharacterPacketHandler;
import taan.online.character.repository.CharacterRepository;
import taan.online.character.service.SelectCharacterAction;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.model.InventoryId;
import taan.online.inventory.model.InventoryItem;
import taan.online.inventory.model.InventoryItemId;
import taan.online.inventory.model.InventoryKind;
import taan.online.server.SelectedCharacterRequestDispatcher;
import taan.online.session.controller.CashRefreshPacketHandler;
import taan.online.session.controller.ClientLoggingPacketHandler;
import taan.online.session.service.SessionChannelAccess;
import taan.online.session.service.SessionCharacterAccess;
import taan.online.session.service.SessionRegistry;

public final class SelectionFixtures {
    private SelectionFixtures() {
    }

    public static void assertChannels(byte[] root, int port) {
        var input = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0x332c0637, input.getInt());
        assertEquals(input.remaining() - 4, input.getInt());
        assertEquals(0x53, input.get());
        assertEquals(0, input.get());

        input.getInt();
        assertEquals("content", string(input));
        assertEquals("", string(input));
        assertEquals(0, input.getInt());
        assertEquals(1, input.getInt());
        assertEquals("server", string(input));
        assertEquals("", string(input));
        assertEquals(Map.of("name", "Solar", "type", "0"), attributes(input));
        assertEquals(2, input.getInt());

        for (int id = 1; id <= 2; id++) {
            assertEquals("channel", string(input));
            assertEquals("", string(input));
            var attributes = attributes(input);

            assertEquals(Integer.toString(id), attributes.get("id"));
            assertEquals(id == 1 ? "Moon" : "Terra", attributes.get("name"));
            assertEquals("1", attributes.get("disable"));
            assertEquals(Integer.toString(port), attributes.get("port"));
            assertEquals("127.0.0.1", attributes.get("ip"));
            assertEquals(0, input.getInt());
        }

        assertEquals(0, input.remaining());
    }

    public static List<Long> assertSelection(byte[] root, long characterId, AccountGender gender) {
        var input = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0x3acb06af, input.getInt());
        assertEquals(1, input.getInt());
        assertEquals(0x22d40519, input.getInt());
        assertEquals(characterId, Integer.toUnsignedLong(input.getInt()));

        string(input);
        assertEquals(1, input.getShort());

        input.position(input.position() + 16);
        assertEquals(gender == AccountGender.MALE ? 1 : 2, input.get());

        input.position(input.position() + 36);
        assertEquals("", string(input));

        var ids = new ArrayList<Long>();
        var source = CharacterFixtures.appearance(gender);

        for (int inventory = 0; inventory < 3; inventory++) {
            assertEquals(1, input.getInt());
            assertEquals(0x123903ce, input.getInt());
            assertTrue(Integer.toUnsignedLong(input.getInt()) > 0);
            assertEquals(0, input.get());
            assertEquals(new int[]{64, 16, 0}[inventory], Byte.toUnsignedInt(input.get()));
            int count = input.getInt();

            assertEquals(inventory == 1 ? 4 : 0, count);

            for (int item = 0; item < count; item++) {
                long uid = input.getLong();
                int slot = Byte.toUnsignedInt(input.get());
                var appearance = source.parts().get(slot);

                assertTrue(uid > 0);
                assertTrue(!ids.contains(uid));
                assertEquals(appearance.itemId(), Integer.toUnsignedLong(input.getInt()));
                assertEquals(1, input.getShort());
                assertEquals(0, input.get());
                assertEquals(65535, Short.toUnsignedInt(input.getShort()));
                assertEquals(0, input.getShort());
                assertEquals(appearance.parameter0(), Short.toUnsignedInt(input.getShort()));
                assertEquals(appearance.parameter1(), Short.toUnsignedInt(input.getShort()));
                assertEquals(appearance.parameter2(), Short.toUnsignedInt(input.getShort()));
                assertEquals(0, input.get());

                ids.add(uid);
            }
        }

        assertEquals(0, input.getInt());
        assertEquals(0, input.getInt());
        assertEquals(0, input.remaining());
        return List.copyOf(ids);
    }

    private static Map<String, String> attributes(ByteBuffer input) {
        int count = input.getInt();
        var result = new HashMap<String, String>();

        for (int index = 0; index < count; index++) {
            result.put(string(input), string(input));
        }

        return result;
    }

    private static String string(ByteBuffer input) {
        byte[] bytes = new byte[input.getInt() * 2];
        input.get(bytes);
        return new String(bytes, StandardCharsets.UTF_16LE);
    }

    public static byte[] fixture(String name) throws IOException {
        try (var input = SelectionFixtures.class.getResourceAsStream("/selection/" + name)) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll(
                    "\\s",
                    ""
                )
            );
        }
    }

    public static List<CharacterInventory> inventories(AccountGender gender) {
        return List.of(
            new CharacterInventory(new InventoryId(10010), InventoryKind.BAG, List.of()),
            new CharacterInventory(
                new InventoryId(10011),
                InventoryKind.EQUIPMENT,
                CharacterFixtures.starter(gender).items().stream().map(
                    item -> new InventoryItem(
                        new InventoryItemId(100 + item.slot()),
                        item.slot(),
                        item.itemId(),
                        1,
                        item.parameter0(),
                        item.parameter1(),
                        item.parameter2()
                    )
                ).toList()
            ),
            new CharacterInventory(new InventoryId(10012), InventoryKind.JEWEL_BOX, List.of())
        );
    }

    public static SelectedCharacterRequestDispatcher dispatcher(
        SessionRegistry sessions,
        CharacterRepository characters
    ) {
        var access = new SessionCharacterAccess(sessions);
        var channelAccess = new SessionChannelAccess(sessions);
        return new SelectedCharacterRequestDispatcher(
            new SelectCharacterPacketHandler(new SelectCharacterAction(access, characters), access),
            new GetChannelListPacketHandler(new GetChannelListAction(channelAccess, directory()), channelAccess),
            new CashRefreshPacketHandler(sessions),
            new ClientLoggingPacketHandler(sessions)
        );
    }

    public static ChannelDirectory directory() {
        return new ChannelDirectory(
            "Solar",
            2181,
            List.of(
                new ChannelDefinition(1, "Moon"),
                new ChannelDefinition(2, "Terra")
            )
        );
    }

    public static byte[] channels() {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x331d0636).array();
    }

    public static byte[] cash(long value, int slot) {
        return ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x1d380495).putInt((int) value).putInt(slot).array();
    }

    public static byte[] logging(long characterId) {
        return ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x10b3037a).putInt((int) characterId).putInt(1)
            .put((byte) 3).put((byte) 1).put((byte) 0).putInt(0).putInt(0).putInt(0).putInt(0).array();
    }
}
