package taan.online.character.repository.jdbc;

import taan.online.logging.SafeLogException;

public final class CharacterPersistenceException extends RuntimeException {
    public CharacterPersistenceException(String operation, Throwable failure) {
        super("Character database operation failed: " + operation, new SafeLogException(failure));
    }
}
