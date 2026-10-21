package taan.online.match.repository.jdbc;

import java.sql.SQLException;

public final class MatchPersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MatchPersistenceException(SQLException failure) {
        super("Match settlement failed", failure);
    }
}
