package taan.online.character.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterName;
import taan.online.character.model.GameCharacter;
import taan.online.character.repository.CharacterCreationTransaction;
import taan.online.character.repository.CharacterRepository;

public final class JdbcCharacterRepository implements CharacterRepository {
    private final DataSource dataSource;
    private final int queryTimeoutSeconds;

    public JdbcCharacterRepository(DataSource dataSource, int queryTimeoutSeconds) {
        this.dataSource = dataSource;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    @Override
    public boolean existsByName(CharacterName name) {
        try (var connection = dataSource.getConnection();
            var statement = connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM characters WHERE name = ?)")) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setString(1, name.value());

            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        } catch (SQLException failure) {
            throw new CharacterPersistenceException("existsByName", failure);
        }
    }

    @Override
    public List<GameCharacter> findByAccount(AccountId accountId) {
        try (var connection = dataSource.getConnection()) {
            return CharacterRowReader.read(connection, accountId, queryTimeoutSeconds);
        } catch (SQLException | IllegalArgumentException failure) {
            throw new CharacterPersistenceException("findByAccount", failure);
        }
    }

    @Override
    public CharacterCreationTransaction beginCreation(AccountId accountId) {
        Connection connection = null;

        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            AccountGender gender;

            try (var statement = connection.prepareStatement("SELECT gender FROM accounts WHERE id = ? FOR UPDATE")) {
                statement.setQueryTimeout(queryTimeoutSeconds);
                statement.setLong(1, accountId.value());

                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new SQLException("Character owner no longer exists", "22000");
                    }

                    gender = CharacterRow.gender(rows.getShort(1));
                }
            }

            return new JdbcCharacterCreationTransaction(connection, accountId, gender, queryTimeoutSeconds);
        } catch (SQLException failure) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }

            throw new CharacterPersistenceException("beginCreation", failure);
        }
    }
}
