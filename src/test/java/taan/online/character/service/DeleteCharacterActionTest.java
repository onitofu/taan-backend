package taan.online.character.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.LogCapture;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.character.service.model.DeleteCharacterOutcome;
import taan.online.character.service.model.DeleteCharacterResult;

class DeleteCharacterActionTest {
    @Test
    void archivesOnlyTheOwnedCharacterAndLogsSuccessAfterCommitWithoutItsName() {
        var access = new TestAccountAccess();
        var repository = new TestCharacterRepository();
        var target = CharacterFixtures.character(10, access.owner, AccountGender.FEMALE, "Luma");
        var remaining = CharacterFixtures.character(11, access.owner, AccountGender.FEMALE, "OtherHero");
        repository.stored.addAll(List.of(target, remaining));
        var action = new DeleteCharacterAction(access, repository);

        try (var logs = new LogCapture()) {
            repository.onCommit = () -> assertFalse(logs.text().contains("character.deletion.succeeded"));
            var result = action.execute(UUID.randomUUID(), 10);

            assertEquals(DeleteCharacterOutcome.DELETED, result.outcome());
            assertEquals(List.of(remaining), result.catalog());
            assertEquals(List.of(target), repository.archived);
            assertEquals(1, repository.commits);
            assertEquals(1, repository.closes);
            assertEquals(0, repository.rollbacks);
            assertTrue(logs.text().contains("character.deletion.succeeded"));
            assertFalse(logs.text().contains("Luma"));
            assertFalse(logs.text().contains("OtherHero"));
            assertThrows(UnsupportedOperationException.class, () -> result.catalog().clear());
        }

        var repeat = action.execute(UUID.randomUUID(), 10);

        assertEquals(DeleteCharacterOutcome.NOT_FOUND, repeat.outcome());
        assertEquals(1, repository.commits);
        assertEquals(1, repository.rollbacks);
        assertEquals(List.of(target), repository.archived);
    }

    @Test
    void concealsMissingAndForeignIdsAndRejectsInvalidIdsBeforeDatabaseWork() {
        var access = new TestAccountAccess();
        var repository = new TestCharacterRepository();
        repository.stored.add(CharacterFixtures.character(20, new AccountId(2), AccountGender.MALE, "MaleHero"));
        var action = new DeleteCharacterAction(access, repository);

        for (long id : new long[]{0, -1, 0x1_0000_0000L, 20, 21}) {
            try (var logs = new LogCapture()) {
                var result = action.execute(UUID.randomUUID(), id);

                assertEquals(DeleteCharacterOutcome.NOT_FOUND, result.outcome());
                assertTrue(result.catalog().isEmpty());
                assertTrue(logs.text().contains("character.deletion.rejected"));
                assertFalse(logs.text().contains("characterId"));
            }
        }

        assertEquals(2, repository.transactions);
        assertEquals(0, repository.commits);
        assertEquals(1, repository.stored.size());

        access.active = false;

        assertEquals(DeleteCharacterOutcome.SESSION_CLOSED, action.execute(UUID.randomUUID(), 20).outcome());
        assertEquals(2, repository.transactions);
        assertThrows(NullPointerException.class, () -> new DeleteCharacterResult(null, List.of()));
    }

    @Test
    void rollsBackCatalogAndCommitFailuresWithoutReportingSuccess() {
        for (boolean duringCommit : new boolean[]{false, true}) {
            var access = new TestAccountAccess();
            var repository = new TestCharacterRepository();
            var target = CharacterFixtures.character(10, access.owner, AccountGender.FEMALE, "Luma");
            repository.stored.add(target);
            Runnable failure = () -> repository.failure = new IllegalStateException("private-value");

            if (duringCommit) {
                repository.onCommit = failure;
            } else {
                repository.onRead = failure;
            }

            try (var logs = new LogCapture()) {
                assertThrows(
                    IllegalStateException.class,
                    () -> new DeleteCharacterAction(access, repository).execute(UUID.randomUUID(), 10)
                );
                assertFalse(logs.text().contains("character.deletion.succeeded"));
                assertFalse(logs.text().contains("private-value"));
                assertEquals(List.of(target), repository.stored);
                assertTrue(repository.archived.isEmpty());
                assertEquals(1, repository.rollbacks);
                assertEquals(1, repository.closes);
            }
        }
    }
}
