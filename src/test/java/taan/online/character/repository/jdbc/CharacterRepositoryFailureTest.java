package taan.online.character.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;
import taan.online.character.repository.CharacterTransaction;
import taan.online.inventory.repository.jdbc.JdbcInventoryRepository;

class CharacterRepositoryFailureTest {
    @Test
    void closesFailedReadsAndNeverExposesSqlFieldValues() {
        for (String stage : List.of("source.getConnection", "statement.executeQuery", "rows.getLong")) {
            var stub = new CharacterJdbcStub();
            stub.failures.add(stage);
            var repository = new JdbcCharacterRepository(
                stub.source(),
                5,
                new JdbcInventoryRepository(
                    stub.source(),
                    5
                )
            );
            var failure = assertThrows(
                CharacterPersistenceException.class,
                () -> repository.findByAccount(new AccountId(1))
            );

            safe(failure);
        }

        var stub = new CharacterJdbcStub();
        stub.failures.add("statement.executeQuery");

        safe(
            assertThrows(
                CharacterPersistenceException.class,
                () -> new JdbcCharacterRepository(
                    stub.source(),
                    5,
                    new JdbcInventoryRepository(
                        stub.source(),
                        5
                    )
                ).existsByName(
                    new CharacterName(
                        "Luma"
                    )
                )
            )
        );
        assertTrue(stub.calls.contains("connection.close"));
    }

    @Test
    void releasesConnectionsWhenOpeningOrLockingTheAccountFailsIncludingCleanupFailure() {
        for (String stage : List.of("source.getConnection", "connection.setAutoCommit", "statement.executeQuery")) {
            var stub = new CharacterJdbcStub();
            stub.failures.add(stage);
            stub.failures.add("connection.close");

            safe(
                assertThrows(
                    CharacterPersistenceException.class,
                    () -> transaction(stub)
                )
            );

            if (!stage.equals("source.getConnection")) {
                assertTrue(stub.calls.contains("connection.close"));
            }
        }

        var missing = new CharacterJdbcStub();
        missing.ownerExists = false;

        assertThrows(
            CharacterPersistenceException.class,
            () -> new JdbcCharacterRepository(
                missing.source(),
                5,
                new JdbcInventoryRepository(
                    missing.source(),
                    5
                )
            ).beginTransaction(
                new AccountId(
                    1
                )
            )
        );

        for (short value : new short[]{0, 3}) {
            var invalid = new CharacterJdbcStub();
            invalid.gender = value;

            assertThrows(
                CharacterPersistenceException.class,
                () -> new JdbcCharacterRepository(
                    invalid.source(),
                    5,
                    new JdbcInventoryRepository(
                        invalid.source(),
                        5
                    )
                ).beginTransaction(
                    new AccountId(
                        1
                    )
                )
            );
        }
    }

    @Test
    void propagatesTransactionFailuresAndAlwaysAttemptsRollbackAndConnectionClosure() {
        var operations = List.of("count", "insert", "batch", "archive", "catalog", "commit", "rollback", "close");

        for (String operation : operations) {
            var stub = new CharacterJdbcStub();
            var transaction = transaction(stub);
            String stage = switch (operation) {
                case "count", "insert", "catalog" -> "statement.executeQuery";
                case "batch" -> "statement.executeBatch";
                case "archive" -> "statement.executeUpdate";
                case "commit" -> "connection.commit";
                case "rollback" -> "connection.rollback";
                default -> "connection.close";
            };
            stub.failures.add(stage);

            var failure = assertThrows(
                CharacterPersistenceException.class,
                () -> {
                    try (transaction) {
                        switch (operation) {
                            case "count" -> transaction.hasCharacters();
                            case "archive" -> transaction.archive(new CharacterId(1));
                            case "insert", "batch" -> transaction.tryInsert(
                                new CharacterName("Luma"),
                                CharacterFixtures.starter(AccountGender.FEMALE)
                            );
                            case "catalog" -> transaction.catalog();
                            case "commit" -> transaction.commit();
                            default -> { }
                        }
                    }
                }
            );

            safe(failure);
            assertTrue(stub.calls.contains("connection.close"));
            assertTrue(stub.calls.contains("connection.rollback"));
        }
    }

    @Test
    void reportsCorruptRowsAndNameConflictsWithoutGuessingMissingState() {
        var stub = new CharacterJdbcStub();
        stub.missingEquipment = true;
        var repository = new JdbcCharacterRepository(stub.source(), 5, new JdbcInventoryRepository(stub.source(), 5));

        assertThrows(CharacterPersistenceException.class, () -> repository.findByAccount(new AccountId(1)));

        try (var transaction = transaction(stub)) {
            assertThrows(CharacterPersistenceException.class, transaction::catalog);
        }

        stub.missingEquipment = false;
        stub.gender = 0;

        assertThrows(CharacterPersistenceException.class, () -> repository.findByAccount(new AccountId(1)));

        stub.gender = 2;
        stub.nullSlot = true;

        assertTrue(
            repository.findByAccount(new AccountId(1)).getFirst().appearance().parts().stream()
                .allMatch(
                    part -> part.itemId() == 0 && part.palette1Id() == 0
                        && part.palette2Id() == 0 && part.palette3Id() == 0
                )
        );

        stub.nameConflict = true;

        try (var transaction = transaction(stub)) {
            assertTrue(
                transaction.tryInsert(
                    new CharacterName("Luma"),
                    CharacterFixtures.starter(AccountGender.FEMALE)
                )
                    .isEmpty()
            );
            assertEquals(AccountGender.FEMALE, transaction.accountGender());
        }
    }

    @Test
    void loadsInventoriesAndWrapsMissingCorruptAndFailedReads() {
        var valid = new CharacterJdbcStub();

        try (var transaction = transaction(valid)) {
            assertEquals(3, transaction.inventories(new CharacterId(1)).size());
        }

        for (int mode : new int[]{0, 1, 2}) {
            var stub = new CharacterJdbcStub();
            stub.incompleteInventories = mode == 0;
            stub.corruptInventory = mode == 1;
            var transaction = transaction(stub);

            if (mode == 2) {
                stub.failures.add("statement.executeQuery");
            }

            try (transaction) {
                safe(
                    assertThrows(
                        CharacterPersistenceException.class,
                        () -> transaction.inventories(new CharacterId(1))
                    )
                );
            }
        }
    }

    private CharacterTransaction transaction(CharacterJdbcStub stub) {
        return new JdbcCharacterRepository(
            stub.source(),
            5,
            new JdbcInventoryRepository(
                stub.source(),
                5
            )
        ).beginTransaction(
            new AccountId(
                1
            )
        );
    }

    private void safe(CharacterPersistenceException failure) {
        assertFalse(failure.getMessage().contains("Luma"));
        assertFalse(failure.getCause().getMessage().contains("Luma"));
        assertFalse(failure.getCause().getMessage().contains("private-database-value"));
    }
}
