package taan.online.room.service;

import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.sessionId;

import java.util.UUID;
import taan.online.channel.service.ChannelRegistry;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.service.model.RoomStartResult;

public final class RequestRoomStartAction {
    private static final EventLogger LOG = EventLogger.forClass(RequestRoomStartAction.class);

    private final SelectedCharacterAccess sessions;
    private final ChannelRegistry channels;
    private final RoomRegistry rooms;

    public RequestRoomStartAction(SelectedCharacterAccess sessions, ChannelRegistry channels, RoomRegistry rooms) {
        this.sessions = sessions;
        this.channels = channels;
        this.rooms = rooms;
    }

    public RoomStartResult execute(UUID id) {
        if (!sessions.hasSelectedCharacter(id)) {
            return new RoomStartResult(true, "");
        }

        var room = rooms.current(id).orElseThrow(() -> new IllegalStateException("Room membership is not active"));
        var context = channels.membership(id);

        if (context.isEmpty() || context.orElseThrow().channelId() != room.channelId()) {
            throw new IllegalStateException("Room channel context is not current");
        }

        LOG.log(RoomLogEvent.START_REJECTED, sessionId(id), roomId(room.id().value()), reason("MATCH_UNAVAILABLE"));
        return new RoomStartResult(false, "Starting a match is currently unavailable.");
    }
}
