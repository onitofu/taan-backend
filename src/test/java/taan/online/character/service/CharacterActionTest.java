package taan.online.character.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.LogCapture;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.character.service.model.ConfirmNameResult;
import taan.online.character.service.model.CreateCharacterCommand;
import taan.online.character.service.model.CreateCharacterOutcome;

class CharacterActionTest {
    @Test
    void checksFormatFilterAndCaseSensitiveAvailabilityWithoutReservingNames() {
        var access = new TestAccountAccess();
        var repository = new TestCharacterRepository();
        var action = new ConfirmNameAction(access, repository, CharacterFixtures.filter());
        var session = UUID.randomUUID();

        assertEquals(ConfirmNameResult.AVAILABLE, action.execute(session, "MaleHero"));
        assertEquals(0, repository.stored.size());

        repository.stored.add(CharacterFixtures.character(1, new AccountId(2), AccountGender.MALE, "MaleHero"));

        assertEquals(ConfirmNameResult.NAME_TAKEN, action.execute(session, "MaleHero"));
        assertEquals(ConfirmNameResult.AVAILABLE, action.execute(session, "malehero"));

        int checked = repository.checks;

        for (String value : new String[]{"abc", "ADMIN", "xxAdMiNxx", "bad name", "a".repeat(19)}) {
            assertEquals(ConfirmNameResult.INVALID_NAME, action.execute(session, value));
        }

        assertEquals(checked, repository.checks);

        access.active = false;

        assertEquals(ConfirmNameResult.SESSION_CLOSED, action.execute(session, "Luma"));
        assertEquals(checked, repository.checks);
    }

    @Test
    void commitsBeforeSuccessAndReturnsDurableSnapshotsWithoutExposingNamesInLogs() {
        var access = new TestAccountAccess();
        var repository = new TestCharacterRepository();
        var action = action(access, repository);
        var session = UUID.randomUUID();

        try (var logs = new LogCapture()) {
            repository.onCommit = () -> assertFalse(logs.text().contains("character.creation.succeeded"));
            var result = action.execute(session, command("Luma", AccountGender.FEMALE));

            assertEquals(CreateCharacterOutcome.CREATED, result.outcome());
            assertEquals(1, repository.commits);
            assertEquals(1, repository.closes);
            assertEquals(0, repository.rollbacks);
            assertEquals(1, result.catalog().size());
            assertEquals(access.owner, result.catalog().getFirst().accountId());
            assertTrue(logs.text().contains("character.creation.succeeded"));
            assertTrue(logs.text().contains("characterId"));
            assertFalse(logs.text().contains("Luma"));

            assertEquals(
                CreateCharacterOutcome.ACCOUNT_HAS_CHARACTERS,
                action.execute(session, command("OtherHero", AccountGender.FEMALE)).outcome()
            );
            assertEquals(1, repository.inserts);
            assertEquals(1, repository.rollbacks);

            var listed = new ListCharactersAction(repository).execute(access.owner);
            repository.stored.clear();

            assertEquals(1, listed.size());
            assertEquals(1, result.catalog().size());
            assertThrows(UnsupportedOperationException.class, () -> listed.clear());
            assertThrows(UnsupportedOperationException.class, () -> result.catalog().clear());
        }
    }

    @Test
    void revalidatesNamesAppearanceAndOwnershipAtCreationAndRollsBackExpectedRejections() {
        var access = new TestAccountAccess();
        var repository = new TestCharacterRepository();
        var action = action(access, repository);
        var session = UUID.randomUUID();

        for (String name : new String[]{"abc", "AdMiN", "x".repeat(19)}) {
            assertEquals(
                CreateCharacterOutcome.INVALID_NAME,
                action.execute(session, command(name, AccountGender.FEMALE)).outcome()
            );
        }

        assertEquals(0, repository.transactions);
        assertEquals(
            CreateCharacterOutcome.INVALID_APPEARANCE,
            action.execute(session, command("Luma", AccountGender.MALE)).outcome()
        );
        assertEquals(0, repository.inserts);
        assertEquals(1, repository.rollbacks);

        repository.conflict = true;

        assertEquals(
            CreateCharacterOutcome.NAME_TAKEN,
            action.execute(session, command("Luma", AccountGender.FEMALE)).outcome()
        );
        assertEquals(2, repository.rollbacks);
        assertTrue(repository.stored.isEmpty());

        access.active = false;
        int started = repository.transactions;

        assertEquals(
            CreateCharacterOutcome.SESSION_CLOSED,
            action.execute(session, command("Luma", AccountGender.FEMALE)).outcome()
        );
        assertEquals(started, repository.transactions);
    }

    @Test
    void doesNotTrustAnEarlierAvailabilityCheckAndPropagatesCommitFailureWithoutSuccess() {
        var access = new TestAccountAccess();
        var repository = new TestCharacterRepository();
        var confirm = new ConfirmNameAction(access, repository, CharacterFixtures.filter());
        var create = action(access, repository);
        var session = UUID.randomUUID();

        assertEquals(ConfirmNameResult.AVAILABLE, confirm.execute(session, "Luma"));

        repository.stored.add(CharacterFixtures.character(20, new AccountId(2), AccountGender.FEMALE, "Luma"));

        assertEquals(
            CreateCharacterOutcome.NAME_TAKEN,
            create.execute(session, command("Luma", AccountGender.FEMALE)).outcome()
        );

        try (var logs = new LogCapture()) {
            repository.onCommit = () -> repository.failure = new IllegalStateException("private-value");

            assertThrows(
                IllegalStateException.class,
                () -> create.execute(session, command("OtherHero", AccountGender.FEMALE))
            );
            assertFalse(logs.text().contains("character.creation.succeeded"));
            assertFalse(logs.text().contains("private-value"));
            assertEquals(1, repository.stored.size());
            assertEquals(0, repository.commits);
        }
    }

    private CreateCharacterAction action(TestAccountAccess access, TestCharacterRepository repository) {
        return new CreateCharacterAction(access, repository, CharacterFixtures.filter(), CharacterFixtures.options());
    }

    private CreateCharacterCommand command(String name, AccountGender gender) {
        return new CreateCharacterCommand(name, CharacterFixtures.appearance(gender));
    }
}
