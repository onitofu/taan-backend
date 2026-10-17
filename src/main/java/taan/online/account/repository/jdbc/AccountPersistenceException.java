package taan.online.account.repository.jdbc;

import java.sql.SQLException;
import taan.online.logging.SafeLogException;

public final class AccountPersistenceException extends RuntimeException {
    public AccountPersistenceException(String operation, SQLException failure) {
        super("Database operation failed: " + operation, new SafeLogException(failure));
    }
}
