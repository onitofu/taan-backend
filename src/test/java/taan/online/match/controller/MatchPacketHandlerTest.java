package taan.online.match.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.MatchTerminalFixture;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.packet.PcGamePlayGiveUp;
import taan.online.match.protocol.packet.PqResultRandSelect;
import taan.online.match.protocol.packet.PqResultSelectCard;
import taan.online.match.service.ConsumeMatchResultAction;
import taan.online.match.service.TerminateMatchAction;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.model.RoomMode;
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
