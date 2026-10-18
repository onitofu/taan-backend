package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.inventory.controller.MoveInventoryItemPacketHandler;
import taan.online.inventory.model.InventoryMove;
import taan.online.inventory.model.ItemCatalog;
import taan.online.inventory.model.ItemDefinition;
import taan.online.inventory.model.ItemGender;
import taan.online.inventory.protocol.packet.Inventory;
import taan.online.inventory.protocol.packet.InventoryItemData;
import taan.online.inventory.repository.InventoryRepository;
import taan.online.inventory.repository.TestInventoryRepository;
import taan.online.inventory.service.InventoryMoveContextAccess;
import taan.online.inventory.service.MoveInventoryItemAction;
import taan.online.inventory.service.TestInventoryMoveContextAccess;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.inventory.service.model.MoveInventoryItemCommand;
import taan.online.server.InventoryRequestDispatcher;
import taan.online.session.service.SessionInventoryAccess;
import taan.online.session.service.SessionRegistry;

public final class InventoryFixtures {
    private InventoryFixtures() {
    }

    public static ItemCatalog catalog() {
        var catalog = new ItemCatalog();
        catalog.initialize(definitions());
        return catalog;
    }

    public static List<ItemDefinition> definitions() {
        var result = new ArrayList<ItemDefinition>();

        try (var reader = new BufferedReader(
            new InputStreamReader(
                InventoryFixtures.class.getResourceAsStream("/inventory/item-definitions.csv"),
                StandardCharsets.US_ASCII
            )
        )) {
            for (String line : reader.lines().toList()) {
                String[] parts = line.split(",", -1);
                var gender = switch (parts[2]) {
                    case "0" -> ItemGender.UNRESTRICTED;
                    case "1" -> ItemGender.MALE;
                    default -> ItemGender.FEMALE;
                };
                result.add(
                    new ItemDefinition(
                        Long.parseLong(parts[0]),
                        "Item" + parts[0],
                        parts[1].isEmpty() ? null : Integer.valueOf(parts[1]),
                        gender,
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]),
                        Integer.parseInt(parts[5]),
                        parts[6].equals("1")
                    )
                );
            }
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }

        return List.copyOf(result);
    }

    public static InventoryRequestDispatcher dispatcher(SessionRegistry sessions, InventoryRepository repository) {
        return dispatcher(sessions, repository, new TestInventoryMoveContextAccess());
    }

    public static InventoryRequestDispatcher dispatcher(
        SessionRegistry sessions,
        InventoryRepository repository,
        InventoryMoveContextAccess contexts
    ) {
        var access = new SessionInventoryAccess(sessions);
        return new InventoryRequestDispatcher(
            new MoveInventoryItemPacketHandler(
                new MoveInventoryItemAction(access, repository, catalog(), contexts),
                access
            )
        );
    }

    public static byte[] move(
        long account,
        long source,
        long uid,
        int from,
        long item,
        long target,
        int to,
        long observed
    ) {
        return ByteBuffer.allocate(34).order(ByteOrder.LITTLE_ENDIAN).putInt(0x4b8707b5).putInt((int) account)
            .putInt((int) source).putLong(uid).put((byte) from).putInt((int) item).putInt((int) target)
            .put((byte) to).putInt((int) observed).array();
    }

    public static InventoryActor actor() {
        return new InventoryActor(UUID.randomUUID(), new AccountId(1), 1001, AccountGender.FEMALE);
    }

    public static MoveInventoryItemCommand command(TestInventoryRepository repository) {
        var source = repository.stored.inventories().get(1);
        var item = source.items().stream().filter(value -> value.slot() == 1).findFirst().orElseThrow();
        return new MoveInventoryItemCommand(
            1,
            new InventoryMove(
                10011,
                item.id().value(),
                1,
                item.itemId(),
                10010,
                79,
                0
            )
        );
    }

    public static byte[] fixture(String name) throws Exception {
        try (var input = InventoryFixtures.class.getResourceAsStream("/inventory/" + name + ".hex")) {
            return HexFormat.of().parseHex(
                new String(
                    input.readAllBytes(),
                    StandardCharsets.US_ASCII
                ).replaceAll(
                    "\\s+",
                    ""
                )
            );
        }
    }

    public static Inventory update(byte[] root) {
        var input = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x3b6206e4, input.getInt());
        assertEquals(0, input.get());
        var inventory = readInventory(input);
        assertEquals(0, input.remaining());
        return inventory;
    }

    public static List<Inventory> selected(byte[] root) {
        var input = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x3acb06af, input.getInt());
        assertEquals(1, input.getInt());
        assertEquals(0x22d40519, input.getInt());
        input.getInt();
        int nameLength = input.getInt();
        input.position(input.position() + nameLength * 2 + 2 + 16 + 1 + 36);
        assertEquals(0, input.getInt());
        var result = new ArrayList<Inventory>();

        for (int count = 0; count < 3; count++) {
            assertEquals(1, input.getInt());
            result.add(readInventory(input));
        }

        assertEquals(0, input.getInt());
        assertEquals(0, input.getInt());
        assertEquals(0, input.remaining());
        return List.copyOf(result);
    }

    private static Inventory readInventory(ByteBuffer input) {
        assertEquals(0x123903ce, input.getInt());
        long id = Integer.toUnsignedLong(input.getInt());
        assertEquals(0, input.get());
        int capacity = Byte.toUnsignedInt(input.get());
        int count = input.getInt();
        var items = new ArrayList<InventoryItemData>();

        for (int index = 0; index < count; index++) {
            long uid = input.getLong();
            int slot = Byte.toUnsignedInt(input.get());
            long item = Integer.toUnsignedLong(input.getInt());
            int quantity = Short.toUnsignedInt(input.getShort());
            assertEquals(0, input.get());
            assertEquals(65535, Short.toUnsignedInt(input.getShort()));
            assertEquals(0, input.getShort());
            int first = Short.toUnsignedInt(input.getShort());
            int second = Short.toUnsignedInt(input.getShort());
            int third = Short.toUnsignedInt(input.getShort());
            assertEquals(0, input.get());
            items.add(new InventoryItemData(uid, slot, item, quantity, first, second, third));
        }

        return new Inventory(id, capacity, items);
    }
}
