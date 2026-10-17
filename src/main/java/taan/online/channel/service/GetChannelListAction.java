package taan.online.channel.service;

import static taan.online.logging.LogField.durationMs;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import taan.online.channel.model.ChannelDirectory;
import taan.online.channel.logging.ChannelLogEvent;
import taan.online.logging.EventLogger;

public final class GetChannelListAction {
    private static final EventLogger LOG = EventLogger.forClass(GetChannelListAction.class);

    private final SelectedCharacterAccess sessions;
    private final ChannelDirectory directory;

    public GetChannelListAction(SelectedCharacterAccess sessions, ChannelDirectory directory) {
        this.sessions = sessions;
        this.directory = directory;
    }

    public Optional<ChannelDirectory> execute(UUID sessionId) {
        long started = System.nanoTime();

        if (!sessions.hasSelectedCharacter(sessionId)) {
            return Optional.empty();
        }

        LOG.log(ChannelLogEvent.LIST_LOADED, durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
        return Optional.of(directory);
    }
}
