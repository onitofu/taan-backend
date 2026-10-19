package taan.online.match.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.match.model.MatchId;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.service.ApplyScenarioAction;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.model.RoomMode;

class ApplyScenarioPacketHandlerTest {
    @Test
    void decodesItsOwnRootAndKeepsProductionCompositionInPreview() throws Exception {
        try (var room = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var matches = MatchFixtures.registry();
            var draft = room.draft();
            var context = new MatchCommandContext(
                new MatchId(draft.token().matchId()),
                draft.token().roomId(),
                draft.token().generation(),
                draft.channelId(),
                room.owner,
                draft.participants().getFirst().membership().admissionId()
            );
            var handler = new ApplyScenarioPacketHandler(new ApplyScenarioAction(matches));
            byte[] root = MatchFixtures.root("native-cannon-0-0");
            assertEquals("STALE_MATCH", handler.handle(context, 1, 0, root).toCompletableFuture().join().reason());
            matches.reserve(draft);
            assertEquals(
                ScenarioApplicationOutcome.IGNORED,
                handler.handle(context, 1, 0, root).toCompletableFuture().join().outcome()
            );
            assertEquals(
                ScenarioApplicationOutcome.IGNORED,
                handler.handle(context, 1, 0, new byte[0]).toCompletableFuture().join().outcome()
            );
            var claims = new ScenarioClaimsMapper().map(new ScenarioCodec().decode(root));
            var command = new ApplyScenarioCommand(context, 1, 0, claims, root);
            byte[] original = root.clone();
            root[0]++;
            assertArrayEquals(original, command.root());
            assertNotSame(command.root(), command.root());
            assertEquals("ApplyScenarioCommand[REDACTED]", command.toString());
            matches.cancel(draft.token(), "TEST");
        }
    }
}
