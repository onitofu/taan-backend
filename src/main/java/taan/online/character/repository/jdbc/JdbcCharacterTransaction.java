package taan.online.character.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.model.StarterEquipment;
import taan.online.inventory.repository.jdbc.JdbcInventoryRepository;
import java.util.Optional;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterAppearance;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;
import taan.online.character.model.GameCharacter;
import taan.online.character.repository.CharacterTransaction;

final class JdbcCharacterTransaction implements CharacterTransaction {
    private final Connection connection;
    private final JdbcInventoryRepository inventoryRepository;
    private final AccountId owner;
    private final AccountGender gender;
    private final int timeout;
    private boolean committed;

    JdbcCharacterTransaction(
        Connection connection,
        AccountId owner,
        AccountGender gender,
        int timeout,
        JdbcInventoryRepository inventoryRepository
    ) {
        this.connection = connection;
        this.inventoryRepository = inventoryRepository;
        this.owner = owner;
        this.gender = gender;
        this.timeout = timeout;
    }

    @Override
    public AccountGender accountGender() {
        return gender;
    }

    @Override
    public boolean hasCharacters() {
        String sql = "SELECT EXISTS (SELECT 1 FROM characters WHERE account_id = ? AND deleted_at IS NULL)";

        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);
            statement.setLong(1, owner.value());

            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        } catch (SQLException failure) {
            throw new CharacterPersistenceException("hasCharacters", failure);
        }
    }

    @Override
    public Optional<GameCharacter> tryInsert(
        CharacterName name,
        CharacterAppearance appearance,
        StarterEquipment starter
    ) {
        String sql = "INSERT INTO characters (account_id, name) VALUES (?, ?) "
            + "ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING RETURNING id, level, created_at";
        Optional<GameCharacter> created;

        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);
            statement.setLong(1, owner.value());
            statement.setString(2, name.value());

            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    created = Optional.of(
                        new GameCharacter(
                            new CharacterId(rows.getLong("id")),
                            owner,
                            name,
                            rows.getInt("level"),
                            gender,
                            appearance,
                            rows.getObject("created_at", OffsetDateTime.class).toInstant()
                        )
                    );
                } else {
                    created = Optional.empty();
                }
            }

            if (created.isPresent()) {
                insertAppearance(created.orElseThrow());
                inventoryRepository.create(connection, created.orElseThrow().id().value(), starter);
            }
        } catch (SQLException failure) {
            throw new CharacterPersistenceException("tryInsert", failure);
        }

        return created;
    }

    @Override
    public List<CharacterInventory> inventories(CharacterId characterId) {
        try {
            return inventoryRepository.read(connection, owner.value(), characterId.value());
        } catch (SQLException | IllegalArgumentException failure) {
            throw new CharacterPersistenceException("inventories", failure);
        }
    }

    @Override
    public boolean archive(CharacterId characterId) {
        String sql = "UPDATE characters SET deleted_at = CURRENT_TIMESTAMP "
            + "WHERE id = ? AND account_id = ? AND deleted_at IS NULL";

        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);
            statement.setLong(1, characterId.value());
            statement.setLong(2, owner.value());
            return statement.executeUpdate() == 1;
        } catch (SQLException failure) {
            throw new CharacterPersistenceException("archive", failure);
        }
    }

    @Override
    public List<GameCharacter> catalog() {
        try {
            return CharacterRowReader.read(connection, owner, timeout);
        } catch (SQLException | IllegalArgumentException failure) {
            throw new CharacterPersistenceException("catalog", failure);
        }
    }

    @Override
    public void commit() {
        try {
            connection.commit();
            committed = true;
        } catch (SQLException failure) {
            throw new CharacterPersistenceException("commit", failure);
        }
    }

    @Override
    public void close() {
        try (connection) {
            if (!committed) {
                connection.rollback();
            }
        } catch (SQLException failure) {
            throw new CharacterPersistenceException("close", failure);
        }
    }

    private void insertAppearance(GameCharacter character) throws SQLException {
        String sql = "INSERT INTO character_appearance_parts "
            + "(character_id, slot, item_id, parameter0, parameter1, parameter2) VALUES (?, ?, ?, ?, ?, ?)";

        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);

            for (var part : character.appearance().parts()) {
                statement.setLong(1, character.id().value());
                statement.setShort(2, (short) part.slot());
                statement.setLong(3, part.itemId());
                statement.setInt(4, part.parameter0());
                statement.setInt(5, part.parameter1());
                statement.setInt(6, part.parameter2());
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }
}
