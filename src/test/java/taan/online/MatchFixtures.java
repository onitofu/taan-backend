package taan.online;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import taan.online.bootstrap.MatchDefinitionLoader;
import taan.online.match.model.MatchDefinitions;
import taan.online.match.controller.MatchPacketHandler;
import taan.online.match.service.MatchRegistry;
import taan.online.match.service.TerminateMatchAction;
import taan.online.match.service.ConsumeMatchResultAction;
import taan.online.room.service.RoomMatchAccess;
import taan.online.room.service.RoomRegistry;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMembership;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.match.service.ConfirmMatchLoadingAction;
import taan.online.match.service.ConfirmMatchPositionAction;
import taan.online.match.service.ReportMatchEnemiesAction;
import taan.online.logging.WarningLogLimiter;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.Random;
import java.util.random.RandomGenerator;
import taan.online.server.MatchRequestDispatcher;

public final class MatchFixtures {
    private MatchFixtures() {
    }

    public static MatchRequestDispatcher dispatcher(RoomMatchAccess rooms, MatchRegistry matches) {
        return new MatchRequestDispatcher(
            new MatchPacketHandler(
                rooms,
                matches,
                new TerminateMatchAction(rooms),
                new ConsumeMatchResultAction(rooms, matches),
                new ConfirmMatchLoadingAction(rooms),
                new ConfirmMatchPositionAction(rooms),
                new ReportMatchEnemiesAction(rooms)
            )
        );
    }

    public static MatchRequestDispatcher dispatcher(RoomRegistry rooms, MatchRegistry matches) {
        return dispatcher(rooms.matchAccess(), matches);
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
        return new MatchRegistry(
            definitions,
            new MatchBootstrapSettings(30_000_000_000L, 20_000_000_000L, 8),
            scheduler,
            () -> scheduler.now,
            random,
            new WarningLogLimiter(
                LoggerFactory.getLogger(MatchRegistry.class),
                10,
                Duration.ofMinutes(1),
                () -> scheduler.now
            )
        );
    }

    public static void loaded(MatchRegistry matches, RoomMatchToken token) {
        var snapshot = matches.current(token).orElseThrow();

        for (var member : snapshot.participants()) {
            matches.deliveryCompleted(matches.progress(token), member.sessionId());
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
            );
        }

        var synchronizedToken = matches.progress(token);
        snapshot.participants().forEach(member -> matches.deliveryCompleted(synchronizedToken, member.sessionId()));
    }

    public static byte[] root(String name) throws IOException {
        try (var input = MatchFixtures.class.getResourceAsStream("/match/" + name + ".root.hex")) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }
    }

    public static MatchDefinitions definitions() {
        return new MatchDefinitionLoader().load(
            MatchFixtures.class.getResourceAsStream("/match/jungle.properties"),
            MatchFixtures.class.getResourceAsStream("/match/jungle-spawns.csv"),
            MatchFixtures.class.getResourceAsStream("/match/weapons.csv")
        );
    }
}
