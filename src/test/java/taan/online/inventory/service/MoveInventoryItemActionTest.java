package taan.online.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import taan.online.InventoryFixtures;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.inventory.model.InventoryMove;
import taan.online.inventory.model.InventoryMoveOutcome;
import taan.online.inventory.repository.TestInventoryRepository;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.inventory.service.model.MoveInventoryItemCommand;

class MoveInventoryItemActionTest {
    @Test
    void commitsBeforeLoggingAndReturnsOrderedFreshSnapshotsWithoutChangingProperties() {
        var actor = InventoryFixtures.actor();
        var repository = new TestInventoryRepository();
        var before = repository.stored.inventories().get(
            1
        ).items().stream().filter(
            item -> item.slot() == 1
        ).findFirst().orElseThrow();
        var action = new MoveInventoryItemAction(id -> Optional.of(actor), repository, InventoryFixtures.catalog());
        var command = InventoryFixtures.command(repository);

        try (var logs = new LogCapture()) {
            repository.onCommit = () -> assertFalse(logs.text().contains("inventory.move.succeeded"));
            var result = action.execute(actor.sessionId(), command);

            assertEquals(InventoryMoveOutcome.MOVED, result.outcome());
            assertEquals(10011, result.inventories().getFirst().id().value());
            assertEquals(10010, result.inventories().getLast().id().value());
            var after = result.inventories().getLast().items().getFirst();

            assertEquals(before.id(), after.id());
            assertEquals(before.itemId(), after.itemId());
            assertEquals(before.palette1Id(), after.palette1Id());
            assertEquals(before.palette2Id(), after.palette2Id());
            assertEquals(before.palette3Id(), after.palette3Id());
            assertEquals(before.quantity(), after.quantity());
            assertEquals(79, after.slot());
            assertEquals(1, repository.commits);
            assertTrue(logs.text().contains("inventory.move.succeeded"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
            assertThrows(UnsupportedOperationException.class, () -> result.inventories().clear());

            repository.onCommit = () -> { };
            var stale = action.execute(actor.sessionId(), command);

            assertEquals(InventoryMoveOutcome.STALE_SOURCE, stale.outcome());
            assertEquals(1, repository.writes);
            assertEquals(2, stale.inventories().size());
        }
    }

    @Test
    void rejectsAccountSpoofingAndTreatsTheSameSlotAsAnUnchangedRequest() {
        var actor = InventoryFixtures.actor();
        var repository = new TestInventoryRepository();
        var command = InventoryFixtures.command(repository);
        var action = new MoveInventoryItemAction(id -> Optional.of(actor), repository, InventoryFixtures.catalog());

        assertEquals(
            InventoryMoveOutcome.ACCOUNT_MISMATCH,
            action.execute(actor.sessionId(), new MoveInventoryItemCommand(2, command.move())).outcome()
        );
        assertEquals(0, repository.writes);
        var same = new MoveInventoryItemCommand(
            1,
            new InventoryMove(
                10011,
                101,
                1,
                command.move().sourceItemId(),
                10011,
                1,
                command.move().sourceItemId()
            )
        );

        assertEquals(InventoryMoveOutcome.NO_CHANGE, action.execute(actor.sessionId(), same).outcome());
        assertEquals(0, repository.writes);
    }

    @Test
    void dropsClosedOrReplacedSessionsAtEveryBoundaryAndRollsBackUncommittedMoves() {
        for (int stage = 0; stage < 6; stage++) {
            var actor = InventoryFixtures.actor();
            var selected = new AtomicReference<>(actor);
            var repository = new TestInventoryRepository();
            var action = new MoveInventoryItemAction(
                id -> Optional.ofNullable(
                    selected.get()
                ),
                repository,
                InventoryFixtures.catalog()
            );
            var command = InventoryFixtures.command(repository);
            Runnable close = () -> selected.set(null);

            switch (stage) {
                case 0 -> close.run();
                case 1 -> repository.onBegin = close;
                case 2 -> repository.onState = close;
                case 3 -> repository.onApply = close;
                case 4 -> repository.onCommit = close;
                default -> repository.onBegin = () -> selected.set(
                    new InventoryActor(
                        actor.sessionId(),
                        actor.accountId(),
                        2000,
                        actor.gender()
                    )
                );
            }

            assertEquals(InventoryMoveOutcome.SESSION_CLOSED, action.execute(actor.sessionId(), command).outcome());
            assertEquals(stage == 4 ? 1 : 0, repository.commits);
            assertEquals(stage == 0 || stage == 4 ? 0 : 1, repository.rollbacks);
        }
    }

    @Test
    void propagatesDatabaseFailuresWithoutLoggingSuccessOrAcknowledgingUncommittedState() {
        for (int stage = 0; stage < 3; stage++) {
            var actor = InventoryFixtures.actor();
            var repository = new TestInventoryRepository();
            var action = new MoveInventoryItemAction(id -> Optional.of(actor), repository, InventoryFixtures.catalog());
            var command = InventoryFixtures.command(repository);
            Runnable fail = () -> repository.failure = new IllegalStateException("PrivateInventoryValue");

            switch (stage) {
                case 0 -> repository.onBegin = fail;
                case 1 -> repository.onApply = fail;
                default -> repository.onCommit = fail;
            }

            try (var logs = new LogCapture()) {
                assertThrows(IllegalStateException.class, () -> action.execute(actor.sessionId(), command));
                assertEquals(0, repository.commits);
                assertFalse(logs.text().contains("inventory.move.succeeded"));
                assertFalse(logs.text().contains("PrivateInventoryValue"));
            }
        }
    }

}
