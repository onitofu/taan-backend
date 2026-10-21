package taan.online.character.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.InventoryFixtures;
import taan.online.LogCapture;
import taan.online.SelectionFixtures;
import taan.online.TestLevels;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.controller.SelectCharacterHandlingOutcome;
import taan.online.character.controller.SelectCharacterPacketHandler;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.character.service.model.SelectCharacterOutcome;
import taan.online.character.service.model.SelectCharacterResult;

class SelectCharacterActionTest {
    @Test
    void rejectsForeignMissingAndClosedSelectionsWithoutChangingSessionState() {
        var access = new TestAccountAccess();
        var repository = repository();
        repository.stored.add(CharacterFixtures.character(2000, new AccountId(2), AccountGender.MALE, "MaleHero"));
        var action = new SelectCharacterAction(access, repository, InventoryFixtures.catalog());

        for (long id : new long[]{0, 2000, 0xffff_ffffL}) {
            try (var logs = new LogCapture()) {
                var result = action.execute(UUID.randomUUID(), id);

                assertEquals(SelectCharacterOutcome.REJECTED, result.outcome());
                assertTrue(logs.text().contains("character.selection.rejected"));
                assertFalse(logs.text().contains("MaleHero"));
                assertFalse(logs.text().contains("characterId"));
            }
        }

        access.active = false;

        assertEquals(SelectCharacterOutcome.IGNORED, action.execute(UUID.randomUUID(), 1001).outcome());
        assertEquals(3, repository.transactions);
        assertEquals(0, repository.commits);
    }

    @Test
    void bindsOnlyAfterSuccessfulReadCommitAndKeepsDurableSnapshotsImmutable() {
        var access = new TestAccountAccess();
        var repository = repository();

        try (var logs = new LogCapture()) {
            repository.onCommit = () -> {
                assertFalse(logs.text().contains("character.selection.succeeded"));
                assertTrue(access.selected == null);
            };
            var result = new SelectCharacterAction(
                access,
                repository,
                InventoryFixtures.catalog()
            ).execute(
                UUID.randomUUID(),
                1001
            );

            assertEquals(SelectCharacterOutcome.SELECTED, result.outcome());
            assertEquals(result.character().id(), access.selected);
            assertEquals(3, result.inventories().size());
            assertEquals(1, repository.commits);
            assertTrue(logs.text().contains("character.selection.succeeded"));
            assertFalse(logs.text().contains("Luma"));
            assertThrows(UnsupportedOperationException.class, () -> result.inventories().clear());
        }

        assertThrows(NullPointerException.class, () -> new SelectCharacterResult(null, null, List.of()));
    }

    @Test
    void propagatesReadAndCommitFailuresWithoutBindingOrLoggingSuccess() {
        for (boolean commit : new boolean[]{false, true}) {
            var access = new TestAccountAccess();
            var repository = repository();
            Runnable fail = () -> repository.failure = new IllegalStateException("private-value");

            if (commit) {
                repository.onCommit = fail;
            } else {
                repository.onInventories = fail;
            }

            try (var logs = new LogCapture()) {
                assertThrows(
                    IllegalStateException.class,
                    () -> new SelectCharacterAction(
                        access,
                        repository,
                        InventoryFixtures.catalog()
                    ).execute(
                        UUID.randomUUID(),
                        1001
                    )
                );
                assertTrue(access.selected == null);
                assertEquals(1, repository.rollbacks);
                assertFalse(logs.text().contains("character.selection.succeeded"));
                assertFalse(logs.text().contains("private-value"));
            }
        }
    }

    @Test
    void handlesRejectionEarlyClosureAndDisconnectsAtEveryAsyncBoundary() {
        var closed = new TestAccountAccess();
        closed.active = false;

        assertEquals(
            SelectCharacterHandlingOutcome.IGNORED,
            handler(closed, repository()).handle(UUID.randomUUID(), new byte[0]).outcome()
        );

        var rejectAccess = new TestAccountAccess();

        assertEquals(
            SelectCharacterHandlingOutcome.REJECTED,
            handler(rejectAccess, repository()).handle(UUID.randomUUID(), CharacterFixtures.select(22)).outcome()
        );

        for (int stage : new int[]{0, 1, 2}) {
            var access = new TestAccountAccess();
            var repository = repository();

            if (stage == 0) {
                access.onAccess = () -> {
                    if (access.calls == 2) {
                        access.active = false;
                    }
                };
            } else if (stage == 1) {
                access.onSelect = () -> access.active = false;
            } else {
                access.onSelectedRead = () -> access.active = false;
            }

            assertEquals(
                SelectCharacterHandlingOutcome.IGNORED,
                handler(access, repository).handle(UUID.randomUUID(), CharacterFixtures.select(1001)).outcome()
            );
            assertEquals(stage == 0 ? 0 : 1, repository.commits);
        }
    }

    private TestCharacterRepository repository() {
        var repository = new TestCharacterRepository();
        repository.stored.add(CharacterFixtures.character(1001, new AccountId(1), AccountGender.FEMALE, "Luma"));
        repository.inventoryData = SelectionFixtures.inventories(AccountGender.FEMALE);
        return repository;
    }

    private SelectCharacterPacketHandler handler(TestAccountAccess access, TestCharacterRepository repository) {
        return new SelectCharacterPacketHandler(
            new SelectCharacterAction(
                access,
                repository,
                InventoryFixtures.catalog()
            ),
            access,
            TestLevels.table()
        );
    }
}
