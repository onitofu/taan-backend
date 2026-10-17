package taan.online.room.service;

import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.service.ChannelRegistry;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.RoomBootstrapDefinition;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomTitle;
import taan.online.room.model.RoomTitleFilter;
import taan.online.room.service.model.CreateRoomCommand;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.room.service.model.CreateRoomResult;

public final class CreateRoomAction {
    private static final EventLogger LOG = EventLogger.forClass(CreateRoomAction.class);

    private final SelectedCharacterAccess sessions;
    private final ChannelRegistry channels;
    private final RoomRegistry rooms;
    private final RoomCharacterLoader characters;
    private final RoomTitleFilter titles;
    private final RoomBootstrapDefinition defaults;

    public CreateRoomAction(
        SelectedCharacterAccess sessions,
        ChannelRegistry channels,
        RoomRegistry rooms,
        RoomCharacterLoader characters,
        RoomTitleFilter titles,
        RoomBootstrapDefinition defaults
    ) {
        this.sessions = sessions;
        this.channels = channels;
        this.rooms = rooms;
        this.characters = characters;
        this.titles = titles;
        this.defaults = defaults;
    }

    public CreateRoomResult execute(UUID id, CreateRoomCommand command, RoomPresenceOutput output) {
        long started = System.nanoTime();

        if (!sessions.hasSelectedCharacter(id)) {
            return ignored();
        }

        if (rooms.current(id).isPresent()) {
            return rejected(id, "ALREADY_IN_ROOM", started);
        }

        var context = channels.membership(id);

        if (context.isEmpty()) {
            return rejected(id, "NO_CHANNEL", started);
        }

        if (context.orElseThrow().phase() != ChannelPresencePhase.LOBBY) {
            return rejected(id, "NOT_IN_LOBBY", started);
        }

        if (!supported(command)) {
            return rejected(id, "UNSUPPORTED_SETTINGS", started);
        }

        RoomTitle title;

        try {
            title = new RoomTitle(command.title());
        } catch (IllegalArgumentException failure) {
            return rejected(id, "INVALID_TITLE", started);
        }

        if (titles.forbids(title)) {
            return rejected(id, "TITLE_FORBIDDEN", started);
        }

        var character = characters.load(context.orElseThrow());

        if (!sessions.hasSelectedCharacter(id)) {
            return ignored();
        }

        if (character.isEmpty()) {
            return rejected(id, "CHARACTER_UNAVAILABLE", started);
        }

        var settings = new RoomSettings(
            title,
            command.gameType() == 0 ? RoomMode.ITEM : RoomMode.NO_ITEM,
            command.rule() == 2 ? RoomRule.BONUS : RoomRule.BASIC,
            defaults.mapId(),
            8
        );
        var owner = new RoomParticipant(id, character.orElseThrow(), defaults.weaponId());
        var result = rooms.create(context.orElseThrow(), settings, owner, output);
        return result.outcome() == CreateRoomOutcome.REJECTED ? rejected(id, result.reason(), started) : result;
    }

    private boolean supported(CreateRoomCommand command) {
        return command.state() == 5 && command.roomGrade() == 0 && command.gameKind() == 0
            && (command.gameType() == 0 || command.gameType() == 1)
            && command.rule() == (command.gameType() == 0 ? 2 : 0)
            && command.playerCount() == 0 && command.capacity() == 8 && !command.protectedRoom()
            && (command.mapId() == 0 || command.mapId() == 1);
    }

    private CreateRoomResult rejected(UUID id, String value, long started) {
        LOG.log(
            RoomLogEvent.CREATION_REJECTED,
            sessionId(id),
            reason(value),
            durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
        );
        return new CreateRoomResult(CreateRoomOutcome.REJECTED, null, value);
    }

    private CreateRoomResult ignored() {
        return new CreateRoomResult(CreateRoomOutcome.IGNORED, null, "SESSION_CLOSED");
    }
}
