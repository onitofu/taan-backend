package taan.online.inventory.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.InventoryFixtures;
import taan.online.inventory.repository.TestInventoryRepository;
import taan.online.inventory.service.MoveInventoryItemAction;
import taan.online.inventory.service.SelectedInventoryAccess;

class MoveInventoryItemPacketHandlerTest {
    @Test
    void mapsCommittedMovesToSnapshotsThenSoundAndRejectsWithoutInventedStatuses() {
        var actor = InventoryFixtures.actor();
        var repository = new TestInventoryRepository();
        var command = InventoryFixtures.command(repository).move();
        SelectedInventoryAccess access = id -> Optional.of(actor);
        var handler = new MoveInventoryItemPacketHandler(
            new MoveInventoryItemAction(
                access,
                repository,
                InventoryFixtures.catalog()
            ),
            access
        );
        byte[] root = InventoryFixtures.move(1, 10011, 101, 1, command.sourceItemId(), 10010, 79, 0);
        var result = handler.handle(actor.sessionId(), root);

        assertEquals(3, result.responses().size());
        assertEquals(0x3b6206e4, type(result.responses().get(0)));
        assertEquals(0x3b6206e4, type(result.responses().get(1)));
        assertEquals(0x4b9907b6, type(result.responses().get(2)));
        assertEquals(2, handler.handle(actor.sessionId(), root).responses().size());
    }

    @Test
    void ignoresClosedSessionsBeforeDecodingDuringTheActionAndBeforeSending() {
        for (int closeAt : new int[]{1, 2, 7}) {
            var actor = InventoryFixtures.actor();
            var repository = new TestInventoryRepository();
            var command = InventoryFixtures.command(repository).move();
            var calls = new AtomicInteger();
            SelectedInventoryAccess access = id -> calls.incrementAndGet() >= closeAt ? Optional.empty() : Optional.of(
                actor
            );
            var handler = new MoveInventoryItemPacketHandler(
                new MoveInventoryItemAction(
                    access,
                    repository,
                    InventoryFixtures.catalog()
                ),
                access
            );
            var result = handler.handle(
                actor.sessionId(),
                closeAt == 1 ? new byte[0] : InventoryFixtures.move(
                    1,
                    10011,
                    101,
                    1,
                    command.sourceItemId(),
                    10010,
                    79,
                    0
                )
            );

            assertTrue(result.ignored());
            assertTrue(result.responses().isEmpty());
        }
    }

    private int type(byte[] value) {
        return ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
}
