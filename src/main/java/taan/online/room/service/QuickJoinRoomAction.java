package taan.online.room.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.service.ChannelRegistry;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.channel.service.model.ChannelMembershipSnapshot;
import taan.online.logging.EventLogger;
import taan.online.logging.LogField;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.RoomBootstrapDefinition;
import taan.online.room.model.RoomParticipant;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.service.model.EnterRoomResult;

public final class QuickJoinRoomAction {
    private static final EventLogger LOG = EventLogger.forClass(QuickJoinRoomAction.class);

    private final SelectedCharacterAccess sessions;
    private final ChannelRegistry channels;
    private final RoomRegistry rooms;
    private final RoomCharacterLoader characters;
    private final RoomBootstrapDefinition defaults;

    public QuickJoinRoomAction(
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

    public EnterRoomResult execute(UUID id, int selector, RoomPresenceOutput output) {
        long started = System.nanoTime();

        if (!sessions.hasSelectedCharacter(id)) {
            return new EnterRoomResult(
                EnterRoomOutcome.IGNORED,
                "SESSION_CLOSED",
                CompletableFuture.completedFuture(null)
            );
        }

        var context = channels.membership(id).orElse(null);

        if (selector != 0xffff) {
            return rejected(id, context, EnterRoomOutcome.REJECTED, "UNSUPPORTED_SELECTOR", started);
        }

        if (rooms.membership(id).isPresent()) {
            return rejected(id, context, EnterRoomOutcome.ALREADY_IN_ROOM, "ALREADY_IN_ROOM", started);
        }

        if (context == null || context.phase() != ChannelPresencePhase.LOBBY) {
            return rejected(id, context, EnterRoomOutcome.REJECTED, "NOT_IN_LOBBY", started);
        }

        var character = characters.load(context);

        if (!sessions.hasSelectedCharacter(id)) {
            return new EnterRoomResult(
                EnterRoomOutcome.IGNORED,
                "SESSION_CLOSED",
                CompletableFuture.completedFuture(null)
            );
        }

        if (character.isEmpty()) {
            return rejected(id, context, EnterRoomOutcome.REJECTED, "CHARACTER_UNAVAILABLE", started);
        }

        var participant = new RoomParticipant(id, character.orElseThrow(), defaults.weaponId());
        var result = rooms.quickJoin(context, participant, output);

        if (result.outcome() == EnterRoomOutcome.ENTERED || result.outcome() == EnterRoomOutcome.IGNORED) {
            return result;
        }

        return rejected(id, context, result.outcome(), result.reason(), started);
    }

    private EnterRoomResult rejected(
        UUID id,
        ChannelMembershipSnapshot context,
        EnterRoomOutcome outcome,
        String why,
        long started
    ) {
        var fields = new ArrayList<>(
            List.of(sessionId(id), reason(why), durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)))
        );

        if (context != null) {
            fields.add(channelId(context.channelId()));
            fields.add(accountId(context.participant().accountId().value()));
            fields.add(characterId(context.participant().characterId().value()));
        }

        LOG.log(RoomLogEvent.QUICK_JOIN_REJECTED, fields.toArray(LogField[]::new));
        return new EnterRoomResult(outcome, why, CompletableFuture.completedFuture(null));
    }
}
