package taan.online;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.bootstrap.ChannelMatchAccess;
import taan.online.bootstrap.RoomDefinitionLoader;
import taan.online.channel.model.ChannelDefinition;
import taan.online.channel.model.ChannelGroupDefinition;
import taan.online.match.model.MatchDefinitionCatalog;
import taan.online.channel.service.ChannelRegistry;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.match.service.MatchCommandExecutor;
import taan.online.match.service.MatchRegistry;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.protocol.BinaryWriter;
import taan.online.room.controller.RoomPacketHandler;
import taan.online.room.controller.RoomReply;
import taan.online.room.model.RoomBootstrapDefinition;
import taan.online.room.model.RoomIdPool;
import taan.online.room.model.RoomMapCatalog;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomTitle;
import taan.online.room.model.RoomTitleFilter;
import taan.online.room.protocol.packet.PqGameSessionChangeGameOption;
import taan.online.room.protocol.packet.PqGameSessionChangeMap;
import taan.online.room.protocol.packet.PqGameSessionChangeTeam;
import taan.online.room.protocol.packet.PqGameSessionChangeWeapon;
import taan.online.room.protocol.packet.PqGameSessionLeave;
import taan.online.room.protocol.packet.PqGameSessionReady;
import taan.online.room.protocol.packet.PqGameSessionUserExpel;
import taan.online.room.service.BeginCaptainSelectionAction;
import taan.online.room.service.ChangeRoomSettingsAction;
import taan.online.room.service.ChangeRoomTeamAction;
import taan.online.room.service.ChangeRoomWeaponAction;
import taan.online.room.service.CreateRoomAction;
import taan.online.room.service.EnterRoomAction;
import taan.online.room.service.ExpelRoomParticipantAction;
import taan.online.room.service.LeaveRoomAction;
import taan.online.room.service.QuickJoinRoomAction;
import taan.online.room.service.RequestRoomStartAction;
import taan.online.room.service.RoomCharacterLoader;
import taan.online.room.service.RoomRegistry;
import taan.online.room.service.SetCaptainCandidateAction;
import taan.online.room.service.SetRoomReadyAction;
import taan.online.room.service.SetRoomSlotAvailabilityAction;
import taan.online.room.service.model.CreateRoomCommand;
import taan.online.server.RoomRequestDispatcher;
import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.session.service.SessionChannelAccess;
import taan.online.session.service.SessionRegistry;

public final class RoomScenario implements AutoCloseable {
    public final SessionRegistry sessions;
    public final SessionChannelAccess access;
    public final ChannelRegistry channels;
    public final TestRoomSelectionScheduler timers;
    public final TestMatchScheduler matchTimers;
    public final MatchRegistry matches;
    public final RoomRegistry rooms;
    public final TestCharacterRepository characters;
    public final CreateRoomAction create;
    public final EnterRoomAction enter;
    public final QuickJoinRoomAction quickJoin;
    public final Map<UUID, CollectingPacketOutput> outputs;
    public CollectingPacketOutput lastOutput;
    public final LeaveRoomAction leave;
    public final RequestRoomStartAction start;
    public final RoomPacketHandler handler;

    public RoomScenario() {
        this(Runnable::run, 64);
    }

    public RoomScenario(MatchCommandExecutor executor, int capacity) {
        this(executor, capacity, null);
    }

    public RoomScenario(int kind) {
        this(Runnable::run, 64, kind);
    }

    public RoomScenario(MatchExecutionMode mode) {
        this(Runnable::run, 64, null, mode);
    }

    private RoomScenario(MatchCommandExecutor executor, int capacity, Integer kind) {
        this(executor, capacity, kind, MatchExecutionMode.PREVIEW);
    }

    private RoomScenario(MatchCommandExecutor executor, int capacity, Integer kind, MatchExecutionMode mode) {
        sessions = new SessionRegistry();
        access = new SessionChannelAccess(sessions);
        channels = new ChannelRegistry(access, 2181);
        channels.initialize(kind == null ? SelectionFixtures.directory().groups() : List.of(
            new ChannelGroupDefinition(
                1,
                "Solar",
                0,
                List.of(
                    new ChannelDefinition(1, "Moon", true, 1, 109, 100, kind),
                    new ChannelDefinition(2, "Terra", true, 1, 109, 100, 0)
                )
            )
        ));
        var baseline = MatchFixtures.definitions();
        var catalog = kind == null ? new MatchDefinitionCatalog(
            baseline.catalogVersion(),
            Map.of(1, baseline.map()),
            baseline.weapons(),
            MatchFixtures.cannonShots(),
            MatchFixtures.missileTerrain()
        ) : MatchFixtures.catalog();
        timers = new TestRoomSelectionScheduler();
        matchTimers = new TestMatchScheduler();
        matches = MatchFixtures.registry(
            catalog,
            matchTimers,
            new Random(1),
            new ChannelMatchAccess(channels),
            executor,
            capacity,
            mode
        );
        rooms = new RoomRegistry(
            channels,
            new RoomIdPool(),
            kind == null ? RoomScenario.maps() : new RoomDefinitionLoader().maps(catalog),
            timers,
            timers::nanoTime,
            new Random(1),
            matches
        );
        characters = new TestCharacterRepository();
        create = new CreateRoomAction(
            access,
            channels,
            rooms,
            new RoomCharacterLoader(characters, InventoryFixtures.catalog()),
            new RoomTitleFilter(List.of("denied")),
            new RoomBootstrapDefinition(1, 0),
            kind == null ? RoomScenario.maps() : new RoomDefinitionLoader().maps(catalog)
        );
        enter = new EnterRoomAction(
            access,
            channels,
            rooms,
            new RoomCharacterLoader(characters, InventoryFixtures.catalog()),
            new RoomBootstrapDefinition(1, 0)
        );
        quickJoin = new QuickJoinRoomAction(
            access,
            channels,
            rooms,
            new RoomCharacterLoader(characters, InventoryFixtures.catalog()),
            new RoomBootstrapDefinition(1, 0)
        );
        outputs = new ConcurrentHashMap<>();
        leave = new LeaveRoomAction(rooms);
        start = new RequestRoomStartAction(rooms);
        handler = new RoomPacketHandler(
            access,
            create,
            enter,
            quickJoin,
            new ExpelRoomParticipantAction(rooms),
            new ChangeRoomSettingsAction(rooms),
            new ChangeRoomTeamAction(rooms),
            new ChangeRoomWeaponAction(rooms),
            new SetRoomReadyAction(rooms),
            new SetRoomSlotAvailabilityAction(rooms),
            leave,
            start,
            new BeginCaptainSelectionAction(rooms),
            new SetCaptainCandidateAction(rooms),
            rooms
        );
    }

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

    public static byte[] expel(long targetCharacterId) {
        return new BinaryWriter().unsignedInt(PqGameSessionUserExpel.TYPE_ID).unsignedInt(targetCharacterId)
            .toByteArray();
    }

    public static RoomMapCatalog maps() {
        return new RoomMapCatalog(Set.of(1, 2, 4, 9, 10, 17));
    }

    public static RoomSettings settings() {
        return new RoomSettings(new RoomTitle("Room"), RoomMode.ITEM, RoomRule.BONUS, 1, 8);
    }

    public RoomParticipant owner(UUID id) {
        var actor = access.selectedCharacter(id).orElseThrow();
        var character = characters.stored.stream()
            .filter(value -> value.id().equals(actor.characterId()))
            .findFirst().orElseThrow();
        return new RoomParticipant(id, character, 0);
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
                    new RoomBootstrapDefinition(1, 0),
                    new RoomDefinitionLoader().maps(MatchFixtures.catalog())
                ),
                new EnterRoomAction(
                    access,
                    channels,
                    rooms,
                    new RoomCharacterLoader(characters, InventoryFixtures.catalog()),
                    new RoomBootstrapDefinition(1, 0)
                ),
                new QuickJoinRoomAction(
                    access,
                    channels,
                    rooms,
                    new RoomCharacterLoader(characters, InventoryFixtures.catalog()),
                    new RoomBootstrapDefinition(1, 0)
                ),
                new ExpelRoomParticipantAction(rooms),
                new ChangeRoomSettingsAction(rooms),
                new ChangeRoomTeamAction(rooms),
                new ChangeRoomWeaponAction(rooms),
                new SetRoomReadyAction(rooms),
                new SetRoomSlotAvailabilityAction(rooms),
                new LeaveRoomAction(rooms),
                new RequestRoomStartAction(rooms),
                new BeginCaptainSelectionAction(rooms),
                new SetCaptainCandidateAction(rooms),
                rooms
            )
        );
    }

    public RoomReply handle(UUID id, int type, byte[] root) {
        lastOutput = new CollectingPacketOutput();
        outputs.put(id, lastOutput);
        return handler.handle(id, type, root, lastOutput);
    }

    public static byte[] settings(int mapId, int gameType, int rule, int ignored) {
        return new BinaryWriter().unsignedInt(Integer.toUnsignedLong(PqGameSessionChangeGameOption.TYPE_ID))
            .unsignedShort(mapId).unsignedShort(gameType).unsignedByte(rule).unsignedByte(ignored).toByteArray();
    }

    public static byte[] map(int mapId) {
        return new BinaryWriter().unsignedInt(PqGameSessionChangeMap.TYPE_ID).unsignedShort(mapId).toByteArray();
    }

    public static byte[] weapon(long characterId, int weaponId) {
        return new BinaryWriter().unsignedInt(Integer.toUnsignedLong(PqGameSessionChangeWeapon.TYPE_ID))
            .unsignedInt(characterId).unsignedShort(weaponId).toByteArray();
    }

    public static byte[] ready(int value) {
        return new BinaryWriter().unsignedInt(PqGameSessionReady.TYPE_ID).unsignedByte(value).toByteArray();
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
