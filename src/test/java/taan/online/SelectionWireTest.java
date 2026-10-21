package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.channel.protocol.codec.ChannelListRequestCodec;
import taan.online.channel.protocol.codec.ChannelListResponseCodec;
import taan.online.channel.protocol.packet.ChannelEntry;
import taan.online.channel.protocol.packet.ChannelGroupEntry;
import taan.online.channel.protocol.packet.PrGetChannelList;
import taan.online.character.controller.SelectCharacterPacketHandler;
import taan.online.character.protocol.codec.SelectCharacterRequestCodec;
import taan.online.character.protocol.codec.SelectCharacterResponseCodec;
import taan.online.character.protocol.packet.CharacterInfo;
import taan.online.character.protocol.packet.PrSelectCharacter;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.character.service.SelectCharacterAction;
import taan.online.character.service.TestAccountAccess;
import taan.online.inventory.protocol.codec.InventoryCodec;
import taan.online.inventory.protocol.packet.Inventory;
import taan.online.inventory.protocol.packet.InventoryItemData;
import taan.online.protocol.MalformedPacketException;
import taan.online.protocol.SBufferEncoder;
import taan.online.session.protocol.codec.ClientLoggingCodec;
import taan.online.session.protocol.codec.UpdateCashCodec;
import taan.online.session.protocol.packet.PcUpdateCash;

class SelectionWireTest {
    @Test
    void matchesIndependentSelectionFixturesForBothGendersAndTheOriginalMinimalReply() throws Exception {
        for (var gender : AccountGender.values()) {
            var access = new TestAccountAccess();
            var characters = new TestCharacterRepository();
            boolean male = gender == AccountGender.MALE;
            long id = male ? 1001 : 1002;
            String name = male ? "MaleHero" : "Luma";
            characters.stored.add(CharacterFixtures.character(id, new AccountId(1), gender, name));
            characters.inventoryData = SelectionFixtures.inventories(gender);
            var handler = new SelectCharacterPacketHandler(
                new SelectCharacterAction(
                    access,
                    characters,
                    InventoryFixtures.catalog()
                ),
                access,
                TestLevels.table()
            );
            var reply = handler.handle(UUID.randomUUID(), CharacterFixtures.select(id)).response();

            assertEquals(80, reply.inventories().getFirst().capacity());
            var reference = new PrSelectCharacter(
                reply.character(),
                reply.inventories().stream().map(
                    value -> new Inventory(value.id(), value.id() == 10010 ? 64 : value.capacity(), value.items())
                ).toList()
            );

            assertArrayEquals(
                CharacterFixtures.withProgress(
                    SelectionFixtures.fixture((male ? "male" : "female") + "-selected.root.hex"),
                    0,
                    0,
                    940
                ),
                new SelectCharacterResponseCodec().encode(reference)
            );
            assertThrows(UnsupportedOperationException.class, () -> reply.inventories().clear());
            assertThrows(UnsupportedOperationException.class, () -> reply.inventories().get(1).items().clear());
        }

        var minimal = new PrSelectCharacter(
            new CharacterInfo(1001, "ProbeHero", 1, 0, 0),
            List.of(
                new Inventory(10010, 0, List.of()),
                new Inventory(10011, 0, List.of()),
                new Inventory(10012, 0, List.of())
            )
        );

        assertArrayEquals(
            SelectionFixtures.fixture("candidate-select-character.packet.hex"),
            new SelectCharacterResponseCodec().encode(minimal)
        );
        assertThrows(IllegalArgumentException.class, () -> new PrSelectCharacter(minimal.character(), List.of()));
        assertEquals(
            0xffff_ffffL,
            new SelectCharacterRequestCodec().decode(CharacterFixtures.select(0xffff_ffffL)).characterId()
        );
    }

    @Test
    void preservesBothUidWordsUnsignedNumericBoundsAndThePermanentExpirySentinel() throws Exception {
        var item = new InventoryItemData(0x0102030405060708L, 255, 0xffff_ffffL, 65535, 0x1234, 0xabcd, 65535);
        var inventory = new Inventory(0xffff_ffffL, 0, List.of(item));

        assertArrayEquals(
            SelectionFixtures.fixture("inventory-high-uid.typed.hex"),
            new InventoryCodec().encode(inventory)
        );
    }

    @Test
    void encodesTheCorrectedSBufferAndDisabledSolarDirectory() throws Exception {
        byte[] corrected = SelectionFixtures.fixture("PrGetChannelList-one-local-S00.body.hex");
        byte[] rawTree = SelectionFixtures.fixture("one-channel-tree.hex");

        assertArrayEquals(Arrays.copyOfRange(corrected, 8, corrected.length), new SBufferEncoder().encode(rawTree));
        assertArrayEquals(new byte[]{0x53, 0, 0, 0, 0, 0}, new SBufferEncoder().encode(new byte[0]));
        assertArrayEquals(
            SelectionFixtures.fixture("solar-disabled.root.hex"),
            new ChannelListResponseCodec().encode(
                new PrGetChannelList(
                    List.of(
                        new ChannelGroupEntry(
                            "Solar",
                            0,
                            List.of(
                                new ChannelEntry(1, "Moon", false, 0, 65535, 0),
                                new ChannelEntry(2, "Terra", false, 0, 65535, 0)
                            )
                        )
                    ),
                    2181
                )
            )
        );
    }

    @Test
    void rejectsTruncatedOversizedAndTrailingRequestsAndPreservesUnknownTelemetryValues() {
        var select = new SelectCharacterRequestCodec();
        var logging = new ClientLoggingCodec();
        var cash = new UpdateCashCodec();
        byte[] selection = CharacterFixtures.select(1001);
        byte[] telemetry = SelectionFixtures.logging(1001);
        byte[] refresh = SelectionFixtures.cash(0xffff_ffffL, 0);

        for (int length = 0; length < selection.length; length++) {
            byte[] shortened = Arrays.copyOf(selection, length);

            assertThrows(MalformedPacketException.class, () -> select.decode(shortened));
        }

        for (int length = 0; length < telemetry.length; length++) {
            byte[] shortened = Arrays.copyOf(telemetry, length);

            assertThrows(MalformedPacketException.class, () -> logging.decode(shortened));
        }

        for (int count : new int[]{0, 2, -1}) {
            byte[] changed = selection.clone();
            ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN).putInt(4, count);

            assertThrows(MalformedPacketException.class, () -> select.decode(changed));
        }

        assertThrows(MalformedPacketException.class, () -> select.decode(Arrays.copyOf(selection, 13)));
        assertThrows(MalformedPacketException.class, () -> logging.decode(Arrays.copyOf(telemetry, 32)));
        assertThrows(MalformedPacketException.class, () -> new ChannelListRequestCodec().decode(new byte[4]));
        assertThrows(
            MalformedPacketException.class,
            () -> new ChannelListRequestCodec().decode(
                Arrays.copyOf(SelectionFixtures.channels(), 5)
            )
        );
        assertEquals(0xffff_ffffL, cash.decode(refresh).balance());
        assertArrayEquals(SelectionFixtures.cash(0, 0), cash.encode(new PcUpdateCash(0, 0)));
        byte[] empty = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x10b3037a).putInt(1001).putInt(0).array();

        assertTrue(logging.decode(empty).records().isEmpty());
        assertEquals(3, logging.decode(telemetry).records().getFirst().code0());
        assertThrows(UnsupportedOperationException.class, () -> logging.decode(telemetry).records().clear());
    }
}
