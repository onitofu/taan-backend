package taan.online.bootstrap;

import static taan.online.logging.LogField.port;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.LoggerFactory;
import taan.online.account.controller.CheckAccountPacketHandler;
import taan.online.account.controller.CreateAccountPacketHandler;
import taan.online.account.repository.jdbc.JdbcAccountRepository;
import taan.online.account.security.Argon2idCredentialHasher;
import taan.online.account.service.CheckAccountAction;
import taan.online.account.service.RegisterAccountAction;
import taan.online.bootstrap.logging.ServerLogEvent;
import taan.online.channel.controller.ChannelCatalogMapper;
import taan.online.channel.controller.GetChannelListPacketHandler;
import taan.online.channel.controller.MoveToChannelPacketHandler;
import taan.online.channel.protocol.codec.ChannelListResponseCodec;
import taan.online.channel.repository.jdbc.JdbcChannelRepository;
import taan.online.channel.service.ChannelRegistry;
import taan.online.channel.service.GetChannelListAction;
import taan.online.channel.service.MoveToChannelAction;
import taan.online.character.controller.ConfirmNamePacketHandler;
import taan.online.character.controller.CreateCharacterPacketHandler;
import taan.online.character.controller.DeleteCharacterPacketHandler;
import taan.online.character.controller.SelectCharacterPacketHandler;
import taan.online.character.repository.jdbc.JdbcCharacterRepository;
import taan.online.character.service.ConfirmNameAction;
import taan.online.character.service.CreateCharacterAction;
import taan.online.character.service.DeleteCharacterAction;
import taan.online.character.service.ListCharactersAction;
import taan.online.character.service.SelectCharacterAction;
import taan.online.inventory.controller.MoveInventoryItemPacketHandler;
import taan.online.inventory.model.ItemCatalog;
import taan.online.inventory.repository.jdbc.JdbcInventoryRepository;
import taan.online.inventory.repository.jdbc.JdbcItemDefinitionRepository;
import taan.online.inventory.service.MoveInventoryItemAction;
import taan.online.lobby.controller.LobbyPacketHandler;
import taan.online.lobby.service.EnterLobbyAction;
import taan.online.lobby.service.LeaveLobbyAction;
import taan.online.lobby.service.ListLobbyRoomsAction;
import taan.online.logging.EventLogger;
import taan.online.logging.WarningLogLimiter;
import taan.online.network.TcpServer;
import taan.online.network.TcpServerSettings;
import taan.online.persistence.DatabaseMigrator;
import taan.online.room.controller.RoomPacketHandler;
import taan.online.room.service.CreateRoomAction;
import taan.online.room.service.LeaveRoomAction;
import taan.online.room.service.RequestRoomStartAction;
import taan.online.room.service.RoomRegistry;
import taan.online.server.ConnectionContext;
import taan.online.server.InventoryRequestDispatcher;
import taan.online.server.LobbyRequestDispatcher;
import taan.online.server.RoomRequestDispatcher;
import taan.online.server.SelectedCharacterRequestDispatcher;
import taan.online.session.controller.CashRefreshPacketHandler;
import taan.online.session.controller.ClientLoggingPacketHandler;
import taan.online.session.controller.UserLoginPacketHandler;
import taan.online.session.service.LoginAction;
import taan.online.session.service.SessionChannelAccess;
import taan.online.session.service.SessionCharacterAccess;
import taan.online.session.service.SessionInventoryAccess;
import taan.online.session.service.SessionRegistry;

public final class Application implements AutoCloseable {
    private static final EventLogger LOG = EventLogger.forClass(Application.class);

    private final ApplicationConfig config;
    private final PGSimpleDataSource dataSource;
    private final ChannelRegistry channels;
    private final RoomRegistry rooms;
    private final ItemCatalog itemCatalog = new ItemCatalog();
    private final ThreadPoolExecutor workers;
    private final TcpServer server;
    private final CountDownLatch terminated = new CountDownLatch(1);
    private final AtomicBoolean closing = new AtomicBoolean();

    public Application(ApplicationConfig config) {
        this.config = config;

        var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger("taan.online").setLevel(Level.valueOf(config.logLevel()));

        var hasher = new Argon2idCredentialHasher(
            config.argonMemoryKiB(),
            config.argonIterations(),
            config.argonParallelism()
        );

        dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[]{config.databaseHost()});
        dataSource.setPortNumbers(new int[]{config.databasePort()});
        dataSource.setDatabaseName(config.databaseName());
        dataSource.setUser(config.databaseUser());
        dataSource.setPassword(config.databasePassword());
        dataSource.setConnectTimeout(config.connectTimeoutSeconds());
        dataSource.setSocketTimeout(config.socketTimeoutSeconds());

        var accounts = new JdbcAccountRepository(dataSource, config.socketTimeoutSeconds());
        var check = new CheckAccountPacketHandler(new CheckAccountAction(accounts));
        var create = new CreateAccountPacketHandler(new RegisterAccountAction(accounts, hasher));

        var sessions = new SessionRegistry();
        var login = new LoginAction(accounts, hasher, sessions);
        var characters = new JdbcCharacterRepository(
            dataSource,
            config.socketTimeoutSeconds(),
            new JdbcInventoryRepository(dataSource, config.socketTimeoutSeconds())
        );
        var inventoryAccess = new SessionInventoryAccess(sessions);
        var inventoryDispatcher = new InventoryRequestDispatcher(
            new MoveInventoryItemPacketHandler(
                new MoveInventoryItemAction(
                    inventoryAccess,
                    new JdbcInventoryRepository(dataSource, config.socketTimeoutSeconds()),
                    itemCatalog
                ),
                inventoryAccess
            )
        );
        var access = new SessionCharacterAccess(sessions);
        var definitions = new CharacterDefinitionLoader();
        var names = definitions.nameFilter(Application.class.getResourceAsStream("/character/name-filters.txt"));
        var options = definitions.creationOptions(
            Application.class.getResourceAsStream("/character/creation-options.csv")
        );
        var confirmName = new ConfirmNamePacketHandler(new ConfirmNameAction(access, characters, names), access);
        var createCharacter = new CreateCharacterPacketHandler(
            new CreateCharacterAction(access, characters, names, options),
            access
        );

        var channelAccess = new SessionChannelAccess(sessions);
        channels = new ChannelRegistry(channelAccess, config.port());
        rooms = new RoomRegistry(channels);
        var roomDefinitions = new RoomDefinitionLoader();
        var roomDispatcher = new RoomRequestDispatcher(
            new RoomPacketHandler(
                channelAccess,
                new CreateRoomAction(
                    channelAccess,
                    channels,
                    rooms,
                    characters,
                    itemCatalog,
                    roomDefinitions.titleFilter(Application.class.getResourceAsStream("/character/name-filters.txt")),
                    roomDefinitions.defaults(Application.class.getResourceAsStream("/room/bootstrap.properties"))
                ),
                new LeaveRoomAction(rooms),
                new RequestRoomStartAction(channelAccess, channels, rooms),
                rooms
            )
        );
        var lobby = new LobbyRequestDispatcher(
            new MoveToChannelPacketHandler(new MoveToChannelAction(channels), channelAccess, config.port()),
            new LobbyPacketHandler(
                new EnterLobbyAction(channels),
                new LeaveLobbyAction(channels),
                channelAccess,
                new ListLobbyRoomsAction(channels, rooms),
                config.maxFrameBytes()
            )
        );
        var selected = new SelectedCharacterRequestDispatcher(
            new SelectCharacterPacketHandler(new SelectCharacterAction(access, characters, itemCatalog), access),
            new GetChannelListPacketHandler(new GetChannelListAction(channelAccess, channels), channelAccess),
            new CashRefreshPacketHandler(sessions),
            new ClientLoggingPacketHandler(sessions),
            config.playableTimeSeconds()
        );

        workers = new ThreadPoolExecutor(
            config.workerThreads(),
            config.workerThreads(),
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(config.workerQueueCapacity()),
            Thread.ofPlatform().name("account-worker-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy()
        );

        var warnings = new WarningLogLimiter(
            LoggerFactory.getLogger("taan.online.network"),
            config.logWarnBurst(),
            Duration.ofSeconds(config.logWarnIntervalSeconds()),
            System::nanoTime
        );

        var settings = new TcpServerSettings(
            config.bindAddress(),
            config.port(),
            config.maxConnections(),
            config.maxFrameBytes(),
            config.idleTimeoutSeconds(),
            config.requestTimeoutSeconds(),
            config.frameAssemblyTimeoutSeconds()
        );
        server = new TcpServer(
            settings,
            warnings,
            () -> new ConnectionContext(
                workers,
                check,
                create,
                warnings,
                config.maxPendingRequests(),
                config.requestTimeoutSeconds(),
                new UserLoginPacketHandler(login, sessions, new ListCharactersAction(characters, itemCatalog)),
                Clock.systemUTC(),
                config.idleTimeoutSeconds(),
                confirmName,
                createCharacter,
                new DeleteCharacterPacketHandler(new DeleteCharacterAction(access, characters), access),
                selected,
                lobby,
                inventoryDispatcher,
                roomDispatcher,
                config.outputBatches(),
                config.outputBytes(),
                config.maxFrameBytes()
            )
        );
    }

    public void start() throws InterruptedException {
        LOG.log(ServerLogEvent.STARTING);

        new DatabaseMigrator().migrate(dataSource);
        itemCatalog.initialize(new JdbcItemDefinitionRepository(dataSource, config.socketTimeoutSeconds()).load());
        channels.initialize(new JdbcChannelRepository(dataSource, config.socketTimeoutSeconds()).load());
        var catalog = new ChannelListResponseCodec().encode(new ChannelCatalogMapper().map(channels.catalog()));
        new LobbyOutputValidator().validate(config, channels.catalog(), catalog.length);

        server.start();

        LOG.log(ServerLogEvent.READY, port(server.port()));
    }

    public void awaitTermination() throws InterruptedException {
        terminated.await();
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }

        LOG.log(ServerLogEvent.STOPPING);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.shutdownTimeoutSeconds());

        try {
            server.stopAccepting();
            workers.shutdown();
            long remaining = Math.max(0, deadline - System.nanoTime() - TimeUnit.SECONDS.toNanos(3));

            if (!workers.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                LOG.log(ServerLogEvent.SHUTDOWN_TIMEOUT);
                workers.shutdownNow();
            }
        } catch (InterruptedException failure) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            server.close();
            rooms.close();
            terminated.countDown();

            LOG.log(ServerLogEvent.STOPPED);
        }
    }
}
