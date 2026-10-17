package taan.online.channel.repository.jdbc;

import taan.online.logging.SafeLogException;

public final class ChannelPersistenceException extends RuntimeException {
    public ChannelPersistenceException(Throwable cause) {
        super("Channel configuration could not be loaded", new SafeLogException(cause));
    }
}
