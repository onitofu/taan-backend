package taan.online;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.slf4j.LoggerFactory;
import taan.online.bootstrap.MatchDefinitionLoader;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.controller.MatchPacketHandler;
import taan.online.match.model.MatchBoxPolicy;
import taan.online.match.model.MatchDefinitionCatalog;
import taan.online.match.model.MatchDefinitions;
import taan.online.match.model.MissileTerrainDefinition;
import taan.online.match.model.TerrainLayerDefinition;
import taan.online.match.model.WeaponAttackDefinition;
import taan.online.match.model.WeaponAttackKey;
import taan.online.match.model.WeaponMissileKey;
import taan.online.match.service.ConfirmMatchLoadingAction;
import taan.online.match.service.ConfirmMatchPositionAction;
import taan.online.match.service.MatchAdmissionAccess;
import taan.online.match.service.MatchCommandExecutor;
import taan.online.match.service.MatchRegistry;
import taan.online.match.service.ReportMatchEnemiesAction;
import taan.online.match.service.SelectMatchRewardAction;
import taan.online.match.service.SettleMatchResultAction;
import taan.online.match.service.TerminateMatchAction;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;
import taan.online.server.MatchRequestDispatcher;

public final class MatchFixtures {
    private static final MatchDefinitionCatalog CATALOG = loadCatalog();

    private MatchFixtures() {
    }

    public static MatchRequestDispatcher dispatcher(MatchRegistry matches) {
        return new MatchRequestDispatcher(
            new MatchPacketHandler(
                matches,
                new TerminateMatchAction(matches),
                new SelectMatchRewardAction(matches),
                new ConfirmMatchLoadingAction(matches),
                new ConfirmMatchPositionAction(matches),
                new ReportMatchEnemiesAction(matches),
                TestLevels.table()
            )
        );
    }

    public static MatchRegistry registry() {
        return registry(definitions());
    }

    public static MatchRegistry registry(MatchDefinitions definitions) {
        return registry(definitions, new TestMatchScheduler());
    }

    public static MatchRegistry registry(TestMatchScheduler scheduler) {
        return registry(definitions(), scheduler);
    }

    public static MatchRegistry registry(MatchDefinitions definitions, TestMatchScheduler scheduler) {
        return registry(definitions, scheduler, new Random(1));
    }

    public static MatchRegistry registry(
        MatchDefinitions definitions,
        TestMatchScheduler scheduler,
        RandomGenerator random
    ) {
        return registry(definitions, scheduler, random, (context, participant) -> true);
    }

    public static MatchRegistry registry(
        MatchDefinitions definitions,
        TestMatchScheduler scheduler,
        RandomGenerator random,
        MatchAdmissionAccess access
    ) {
        return registry(definitions, scheduler, random, access, Runnable::run, 64);
    }

    public static MatchRegistry registry(
        MatchDefinitions definitions,
        TestMatchScheduler scheduler,
        RandomGenerator random,
        MatchAdmissionAccess access,
        MatchCommandExecutor executor,
        int capacity
    ) {
        return registry(
            new MatchDefinitionCatalog(
                definitions.catalogVersion(),
                Map.of(definitions.map().mapId(), definitions.map()),
                definitions.weapons(),
                definitions.attacks(),
                MatchFixtures.missileTerrain()
            ),
            scheduler,
            random,
            access,
            executor,
            capacity
        );
    }

    public static MatchRegistry registry(
        MatchDefinitionCatalog catalog,
        TestMatchScheduler scheduler,
        RandomGenerator random,
        MatchAdmissionAccess access,
        MatchCommandExecutor executor,
        int capacity
    ) {
        return registry(catalog, scheduler, random, access, executor, capacity, MatchExecutionMode.PREVIEW);
    }

    public static MatchRegistry registry(
        MatchDefinitionCatalog catalog,
        TestMatchScheduler scheduler,
        RandomGenerator random,
        MatchAdmissionAccess access,
        MatchCommandExecutor executor,
        int capacity,
        MatchExecutionMode mode
    ) {
        return registry(catalog, scheduler, random, access, executor, capacity, mode, MatchLootPolicy.disabled());
    }

    public static MatchRegistry registry(
        MatchDefinitionCatalog catalog,
        TestMatchScheduler scheduler,
        RandomGenerator random,
        MatchAdmissionAccess access,
        MatchCommandExecutor executor,
        int capacity,
        MatchExecutionMode mode,
        MatchLootPolicy policy
    ) {
        return registry(catalog, scheduler, random, access, executor, capacity, mode, policy, MatchBoxPolicy.DISABLED);
    }

    public static MatchRegistry registry(
        MatchDefinitionCatalog catalog,
        TestMatchScheduler scheduler,
        RandomGenerator random,
        MatchAdmissionAccess access,
        MatchCommandExecutor executor,
        int capacity,
        MatchExecutionMode mode,
        MatchLootPolicy policy,
        MatchBoxPolicy boxes
    ) {
        return registry(
            catalog,
            scheduler,
            random,
            access,
            executor,
            capacity,
            mode,
            policy,
            boxes,
            TestMatchResults.action()
        );
    }

    public static MatchRegistry registry(
        MatchDefinitionCatalog catalog,
        TestMatchScheduler scheduler,
        RandomGenerator random,
        MatchAdmissionAccess access,
        MatchCommandExecutor executor,
        int capacity,
        MatchExecutionMode mode,
        MatchLootPolicy policy,
        MatchBoxPolicy boxes,
        SettleMatchResultAction settlements
    ) {
        return new MatchRegistry(
            catalog,
            new MatchBootstrapSettings(
                30_000_000_000L,
                20_000_000_000L,
                8,
                mode,
                20_000_000_000L,
                10_000_000_000L,
                policy,
                boxes
            ),
            scheduler,
            () -> scheduler.now,
            random,
            new WarningLogLimiter(
                LoggerFactory.getLogger(MatchRegistry.class),
                10,
                Duration.ofMinutes(1),
                () -> scheduler.now
            ),
            executor,
            capacity,
            access,
            settlements
        );
    }

    public static void loaded(MatchRegistry matches, RoomMatchToken token) {
        var snapshot = matches.current(token).orElseThrow();

        for (var member : snapshot.participants()) {
            matches.deliveryCompleted(matches.progress(token), member.sessionId()).toCompletableFuture().join();
            matches.loadingDone(
                new RoomMatchContext(
                    new RoomMembership(
                        member.sessionId(),
                        token.roomId(),
                        token.generation(),
                        snapshot.channelId(),
                        member.admissionId()
                    ),
                    token
                ),
                member.slotIndex(),
                member.spawn().settledX(),
                member.spawn().settledY()
            ).toCompletableFuture().join();
        }

        var synchronizedToken = matches.progress(token);
        snapshot.participants().forEach(
            member -> matches.deliveryCompleted(synchronizedToken, member.sessionId()).toCompletableFuture().join()
        );
    }

    public static byte[] endGame(int winner) throws IOException {
        String name = "/match/EndGame-result" + winner + "-no-awards.frame.hex";
        try (var input = MatchFixtures.class.getResourceAsStream(name)) {
            var frame = HexFormat.of().parseHex(new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                .replaceAll("\\s+", ""));
            return Arrays.copyOfRange(frame, 4, frame.length);
        }
    }

    public static byte[] root(String name) throws IOException {
        try (var input = MatchFixtures.class.getResourceAsStream("/match/" + name + ".root.hex")) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }
    }

    public static Map<WeaponAttackKey, WeaponAttackDefinition> attacks() {
        return catalog().attacks();
    }

    public static WeaponAttackDefinition cannonAttack() {
        return attacks().get(new WeaponAttackKey(0, 0));
    }

    public static MatchDefinitions definitions() {
        return catalog().select(1);
    }

    public static List<TerrainLayerDefinition> terrainLayers() {
        return catalog().select(1).map().terrainLayers();
    }

    public static Map<WeaponMissileKey, MissileTerrainDefinition> missileTerrain() {
        return catalog().missiles();
    }

    public static MatchDefinitionCatalog catalog() {
        return CATALOG;
    }

    private static MatchDefinitionCatalog loadCatalog() {
        return new MatchDefinitionLoader().load(
            MatchFixtures.class.getResourceAsStream("/match/catalog.properties"),
            MatchFixtures.class.getResourceAsStream("/match/maps.csv"),
            MatchFixtures.class.getResourceAsStream("/match/map-spawns.csv"),
            MatchFixtures.class.getResourceAsStream("/match/weapons.csv"),
            MatchFixtures.class.getResourceAsStream("/match/weapon-attacks.csv")
        );
    }
}
