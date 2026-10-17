package taan.online.room.service;

import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.sessionId;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.service.ChannelRegistry;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.RoomBootstrapDefinition;
import taan.online.room.model.RoomParticipant;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.service.model.EnterRoomResult;

public final class EnterRoomAction {
    private static final EventLogger LOG = EventLogger.forClass(EnterRoomAction.class);

    private final SelectedCharacterAccess sessions;
    private final ChannelRegistry channels;
    private final RoomRegistry rooms;
    private final RoomCharacterLoader characters;
    private final RoomBootstrapDefinition defaults;

    public EnterRoomAction(
        SelectedCharacterAccess sessions,
        ChannelRegistry channels,
        RoomRegistry rooms,
        RoomCharacterLoader characters,
        RoomBootstrapDefinition defaults
    ) {
        this.sessions = sessions;
        this.channels = channels;
        this.rooms = rooms;
        this.characters = characters;
        this.defaults = defaults;
    }

    public EnterRoomResult execute(UUID id, int requestedRoomId, RoomPresenceOutput output) {
        long started = System.nanoTime();

        if (!sessions.hasSelectedCharacter(id)) {
            return ignored();
        }

        if (rooms.membership(id).isPresent()) {
            return rejected(id, requestedRoomId, EnterRoomOutcome.ALREADY_IN_ROOM, "ALREADY_IN_ROOM", started);
        }

        var context = channels.membership(id);

        if (context.isEmpty() || context.orElseThrow().phase() != ChannelPresencePhase.LOBBY) {
            return rejected(id, requestedRoomId, EnterRoomOutcome.REJECTED, "NOT_IN_LOBBY", started);
        }

        var member = context.orElseThrow();
        var target = rooms.entryTarget(member, requestedRoomId);

        if (target.isEmpty()) {
            return rejected(id, requestedRoomId, EnterRoomOutcome.NOT_FOUND, "ROOM_UNAVAILABLE", started);
        }

        var character = characters.load(member);

        if (!sessions.hasSelectedCharacter(id)) {
            return ignored();
        }

        if (character.isEmpty()) {
            return rejected(id, requestedRoomId, EnterRoomOutcome.REJECTED, "CHARACTER_UNAVAILABLE", started);
        }

        var participant = new RoomParticipant(id, character.orElseThrow(), defaults.weaponId());
        var result = rooms.enter(member, target.orElseThrow(), participant, output);

        if (result.outcome() == EnterRoomOutcome.ENTERED || result.outcome() == EnterRoomOutcome.IGNORED) {
            return result;
        }

        return rejected(id, requestedRoomId, result.outcome(), result.reason(), started);
    }

    private EnterRoomResult rejected(UUID id, int requestedRoomId, EnterRoomOutcome outcome, String why, long started) {
        LOG.log(
            RoomLogEvent.ENTRY_REJECTED,
            sessionId(id),
            roomId(requestedRoomId),
            reason(why),
            durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
        );
        return new EnterRoomResult(outcome, why, CompletableFuture.completedFuture(null));
    }

    private EnterRoomResult ignored() {
        return new EnterRoomResult(EnterRoomOutcome.IGNORED, "SESSION_CLOSED", CompletableFuture.completedFuture(null));
    }
}
