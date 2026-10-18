package taan.online;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.channel.service.ChannelRegistry;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.protocol.BinaryWriter;
import taan.online.room.controller.RoomPacketHandler;
import taan.online.room.controller.RoomReply;
import taan.online.room.model.RoomBootstrapDefinition;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomTitle;
import taan.online.room.model.RoomTitleFilter;
import taan.online.room.protocol.packet.PqGameSessionChangeTeam;
import taan.online.room.protocol.packet.PqGameSessionLeave;
import taan.online.room.service.ChangeRoomTeamAction;
import taan.online.room.service.CreateRoomAction;
import taan.online.room.service.EnterRoomAction;
import taan.online.room.service.LeaveRoomAction;
import taan.online.room.service.QuickJoinRoomAction;
import taan.online.room.service.RequestRoomStartAction;
import taan.online.room.service.RoomCharacterLoader;
import taan.online.room.service.RoomRegistry;
import taan.online.room.service.SetRoomSlotAvailabilityAction;
import taan.online.room.service.model.CreateRoomCommand;
import taan.online.server.RoomRequestDispatcher;
import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.session.service.SessionChannelAccess;
import taan.online.session.service.SessionRegistry;

public final class RoomScenario implements AutoCloseable {
    public final SessionRegistry sessions = new SessionRegistry();
    public final SessionChannelAccess access = new SessionChannelAccess(sessions);
    public final ChannelRegistry channels = SelectionFixtures.registry(access);
    public final RoomRegistry rooms = new RoomRegistry(channels);
    public final TestCharacterRepository characters = new TestCharacterRepository();
    public final CreateRoomAction create = new CreateRoomAction(
        access,
        channels,
        rooms,
        new RoomCharacterLoader(characters, InventoryFixtures.catalog()),
        new RoomTitleFilter(List.of("denied")),
        new RoomBootstrapDefinition(1, 3)
    );
    public final EnterRoomAction enter = new EnterRoomAction(
        access,
        channels,
        rooms,
        new RoomCharacterLoader(characters, InventoryFixtures.catalog()),
        new RoomBootstrapDefinition(1, 3)
    );
    public final QuickJoinRoomAction quickJoin = new QuickJoinRoomAction(
        access,
        channels,
        rooms,
        new RoomCharacterLoader(characters, InventoryFixtures.catalog()),
        new RoomBootstrapDefinition(1, 3)
    );
    public final Map<UUID, CollectingPacketOutput> outputs = new ConcurrentHashMap<>();
    public CollectingPacketOutput lastOutput;
    public final LeaveRoomAction leave = new LeaveRoomAction(rooms);
    public final RequestRoomStartAction start = new RequestRoomStartAction(access, channels, rooms);
    public final RoomPacketHandler handler = new RoomPacketHandler(
        access,
        create,
        enter,
        quickJoin,
        new ChangeRoomTeamAction(rooms),
        new SetRoomSlotAvailabilityAction(rooms),
        leave,
        start,
        rooms
    );

    public UUID player(long id, int channel) {
        var character = CharacterFixtures.character(id, new AccountId(id), AccountGender.FEMALE, "Hero" + id);
        characters.stored.add(character);
        var session = sessions.open();
        sessions.tryClaim(session, new AuthenticatedPrincipal(character.accountId(), character.gender()));
        sessions.trySelect(session, character);

        if (channel != 0) {
            channels.move(session.value(), new MoveToCommand(0, channel, 0, 929));
            lobby(session.value());
        }

        return session.value();
    }

    public void lobby(UUID id) {
        channels.enter(id, RoomListingFixtures.view(id, channels, rooms, new CollectingPacketOutput()));
    }

    public static RoomSettings settings() {
        return new RoomSettings(new RoomTitle("Room"), RoomMode.ITEM, RoomRule.BONUS, 1, 8);
    }

    public RoomParticipant owner(UUID id) {
        var actor = access.selectedCharacter(id).orElseThrow();
        var character = characters.stored.stream()
            .filter(value -> value.id().equals(actor.characterId()))
            .findFirst().orElseThrow();
        return new RoomParticipant(id, character, 3);
    }

    public static CreateRoomCommand command() {
        return new CreateRoomCommand(5, 0, "Room", 0, 0, 2, 0, 8, null, 0);
    }

    public static RoomRequestDispatcher dispatcher(
        SessionRegistry sessions,
        ChannelRegistry channels,
        TestCharacterRepository characters,
        RoomRegistry rooms
    ) {
        var access = new SessionChannelAccess(sessions);
        return new RoomRequestDispatcher(
            new RoomPacketHandler(
                access,
                new CreateRoomAction(
                    access,
                    channels,
                    rooms,
                    new RoomCharacterLoader(characters, InventoryFixtures.catalog()),
                    new RoomTitleFilter(List.of()),
                    new RoomBootstrapDefinition(1, 3)
                ),
                new EnterRoomAction(
                    access,
                    channels,
                    rooms,
                    new RoomCharacterLoader(characters, InventoryFixtures.catalog()),
                    new RoomBootstrapDefinition(1, 3)
                ),
                new QuickJoinRoomAction(
                    access,
                    channels,
                    rooms,
                    new RoomCharacterLoader(characters, InventoryFixtures.catalog()),
                    new RoomBootstrapDefinition(1, 3)
                ),
                new ChangeRoomTeamAction(rooms),
                new SetRoomSlotAvailabilityAction(rooms),
                new LeaveRoomAction(rooms),
                new RequestRoomStartAction(access, channels, rooms),
                rooms
            )
        );
    }

    public RoomReply handle(UUID id, int type, byte[] root) {
        lastOutput = new CollectingPacketOutput();
        outputs.put(id, lastOutput);
        return handler.handle(id, type, root, lastOutput);
    }

    public static byte[] team(int value) {
        return new BinaryWriter().unsignedInt(PqGameSessionChangeTeam.TYPE_ID).unsignedByte(value).toByteArray();
    }

    public static byte[] leave(int room, int reason) {
        return new BinaryWriter().unsignedInt(PqGameSessionLeave.TYPE_ID).unsignedShort(room).unsignedByte(reason)
            .toByteArray();
    }

    @Override
    public void close() {
        rooms.close();
    }
}
