package taan.online.match.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import taan.online.CollectingPacketOutput;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.MatchTerminalFixture;
import taan.online.TestMatchScheduler;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PcGamePlayGiveUp;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.PqResultRandSelect;
import taan.online.match.protocol.packet.PqResultSelectCard;
import taan.online.match.service.ConfirmMatchLoadingAction;
import taan.online.match.service.ConfirmMatchPositionAction;
import taan.online.match.service.ConfirmScenarioAction;
import taan.online.match.service.ConsumeMatchResultAction;
import taan.online.match.service.MatchRegistry;
import taan.online.match.service.ReportMatchEnemiesAction;
import taan.online.match.service.TerminateMatchAction;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.model.RoomMode;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchPacketHandlerTest {
    @Test
    void consumesBothCardTypesWithoutRepliesOrRewardsAndInvalidatesReceiptsOnRestart() throws Exception {
        try (var f = new MatchTerminalFixture(2, RoomMode.ITEM, MatchPhase.PREVIEW); var logs = new LogCapture()) {
            var id = f.room.owner;
            var handler = f.handler;
            var before = handler.capture(id);
            byte[] giveUp = MatchFixtures.root("give-up-empty");
            byte[] random = MatchFixtures.root("PqResult_RandSelect.captured");
            byte[] card = MatchFixtures.root("PqResult_SelectCard");
            handler.handle(id, PqResultRandSelect.TYPE_ID, random, before);

            assertEquals(0, f.outputs.getFirst().ends);
            handler.handle(id, PcGamePlayGiveUp.TYPE_ID, giveUp, before).toCompletableFuture().join();
            var ended = handler.capture(id);

            for (int attempt = 0; attempt < 3; attempt++) {
                handler.handle(id, PqResultRandSelect.TYPE_ID, random, ended);
                handler.handle(id, PqResultSelectCard.TYPE_ID, card, ended);
                handler.handle(id, PcGamePlayGiveUp.TYPE_ID, giveUp, ended);
            }

            assertEquals(1, f.outputs.getFirst().ends);
            assertEquals(1, f.outputs.get(1).ends);
            var rooms = f.room.scenario.rooms;
            rooms.setReady(f.room.players.get(1), true);
            var next = rooms.reserveMatch(id).token();
            handler.handle(id, PqResultRandSelect.TYPE_ID, random, ended);
            handler.handle(id, PcGamePlayGiveUp.TYPE_ID, giveUp, before);

            assertTrue(f.room.scenario.matches.current(next).isPresent());
            assertFalse(new ConsumeMatchResultAction(f.room.scenario.matches).execute(id, ended.roomContext()));
            assertNull(
                new TerminateMatchAction(f.room.scenario.matches).execute(
                    UUID.randomUUID(),
                    ended.roomContext()
                ).toCompletableFuture().join().termination()
            );
            assertNull(
                new TerminateMatchAction(f.room.scenario.matches).execute(
                    id,
                    null
                ).toCompletableFuture().join().termination()
            );
            assertFalse(
                new ConsumeMatchResultAction(f.room.scenario.matches).execute(UUID.randomUUID(), ended.roomContext())
            );
            assertFalse(new ConsumeMatchResultAction(f.room.scenario.matches).execute(id, null));
            handler.handle(id, PcGamePlayGiveUp.TYPE_ID, giveUp, null);

            assertTrue(f.room.scenario.matches.current(next).isPresent());
            assertTrue(logs.text().contains("match.result.selection.ignored"));
            assertTrue(logs.text().contains("NO_AWARDS"));
            assertFalse(logs.text().contains("Hero"));
        }
    }

    @Test
    void refusesAnonymousAndMismatchedBindingsAndCommandsForAnAlreadyRemovedMatch() throws Exception {
        try (var room = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var scheduler = new TestMatchScheduler();
            var matches = new MatchRegistry(
                MatchFixtures.catalog(),
                new MatchBootstrapSettings(30, 20, 8, MatchExecutionMode.CLIENT_SCENARIO),
                scheduler,
                () -> scheduler.now,
                new Random(1),
                new WarningLogLimiter(
                    LoggerFactory.getLogger(MatchRegistry.class),
                    10,
                    Duration.ofSeconds(1),
                    () -> 0L
                ),
                Runnable::run,
                8,
                (context, participant) -> true
            );
            var draft = room.draft();
            matches.reserve(draft);
            var context = new RoomMatchContext(draft.participants().getFirst().membership(), draft.token());
            var binding = new MatchRequestContext(context, 1, 0, Optional.empty());
            var handler = new MatchPacketHandler(
                matches,
                new TerminateMatchAction(matches),
                new ConsumeMatchResultAction(matches),
                new ConfirmMatchLoadingAction(matches),
                new ConfirmMatchPositionAction(matches),
                new ReportMatchEnemiesAction(matches)
            );
            byte[] ack = {0x6d, 0x08, 0x0b, 0x5e, 0, 0};
            handler.handle(room.owner, PcGamePlayEndScenario.TYPE_ID, ack, null).toCompletableFuture().join();
            handler.handle(UUID.randomUUID(), PcGamePlayEndScenario.TYPE_ID, ack, binding).toCompletableFuture().join();
            handler.handle(UUID.randomUUID(), PcGamePlayScenario.TYPE_ID, new byte[0], binding)
                .toCompletableFuture().join();
            assertTrue(matches.current(draft.token()).isPresent());
            assertFalse(matches.scenarioEnabled(null));
            matches.cancel(draft.token(), "TEST_FINISHED");
            assertFalse(matches.scenarioEnabled(context));
            assertFalse(new ConfirmScenarioAction(matches).execute(new ConfirmScenarioCommand(
                MatchCommandContext.from(context),
                1,
                0,
                Optional.empty(),
                0
            )).toCompletableFuture().join());
        }

        try (var f = new MatchTerminalFixture(2, RoomMode.ITEM, MatchPhase.PREVIEW)) {
            assertFalse(f.room.scenario.matches.scenarioEnabled(f.handler.capture(f.room.owner).roomContext()));
        }
    }

    @Test
    void validatesExactBodiesEvenWithoutAMatchAndRejectsServerOnlyMessages() throws Exception {
        try (var f = new MatchTerminalFixture(2, RoomMode.ITEM, MatchPhase.PREVIEW)) {
            for (var entry : new Object[][]{
                {PcGamePlayGiveUp.TYPE_ID, "give-up-empty"},
                {PqResultRandSelect.TYPE_ID, "PqResult_RandSelect.captured"},
                {PqResultSelectCard.TYPE_ID, "PqResult_SelectCard"}
            }) {
                int type = (int) entry[0];
                byte[] root = MatchFixtures.root((String) entry[1]);

                for (int length = 0; length < root.length; length++) {
                    byte[] truncated = Arrays.copyOf(root, length);
                    assertThrows(
                        MalformedPacketException.class,
                        () -> f.handler.handle(f.room.owner, type, truncated, null)
                    );
                }

                assertThrows(
                    MalformedPacketException.class,
                    () -> f.handler.handle(f.room.owner, type, Arrays.copyOf(root, root.length + 1), null)
                );
            }

            assertThrows(
                MalformedPacketException.class,
                () -> f.handler.handle(f.room.owner, 0x57260801, new byte[4], null)
            );
            f.room.scenario.rooms.terminateMatch(f.token, RoomMatchEndReason.GIVE_UP);
            var output = new CollectingPacketOutput();
            var fresh = UUID.randomUUID();
            f.handler.open(fresh, output);

            assertNull(f.handler.capture(fresh).roomContext());
            f.handler.close(fresh);
            f.handler.close(fresh);
        }
    }
}
