package taan.online.lobby.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.ChannelFixtures;
import taan.online.CollectingPacketOutput;
import taan.online.SelectionFixtures;
import taan.online.channel.controller.ChannelHandlingOutcome;
import taan.online.channel.controller.MoveToChannelPacketHandler;
import taan.online.channel.service.ChannelTestAccess;
import taan.online.channel.service.MoveToChannelAction;
import taan.online.lobby.service.EnterLobbyAction;
import taan.online.lobby.service.LeaveLobbyAction;

class LobbyControllerTest {
    @Test
    void ignoresClosedSessionsBeforeDecodeAndInvalidatesResultsIfTheSessionClosesDuringProcessing() {
        for (int closeAt : new int[]{0, 2, 3}) {
            var access = new ChannelTestAccess();
            var id = access.add(1, 1);
            var registry = SelectionFixtures.registry(access);
            var move = new MoveToChannelPacketHandler(new MoveToChannelAction(registry), access, 2181);
            var calls = new AtomicInteger();
            access.onAccess = () -> {
                if (calls.incrementAndGet() == closeAt) {
                    access.players.clear();
                }
            };

            if (closeAt == 0) {
                access.players.clear();
            }

            var result = move.handle(id, closeAt == 0 ? new byte[0] : ChannelFixtures.move(1));

            assertEquals(ChannelHandlingOutcome.IGNORED, result.outcome());

            move.close(id, "CLOSED");

            assertEquals(0, registry.catalog().fills().get(1));
        }
    }

    @Test
    void handlesRepeatedLeaveWithoutMembershipAndClosureBetweenControllerAndRegistry() {
        for (boolean entering : new boolean[]{true, false}) {
            var access = new ChannelTestAccess();
            var id = access.add(1, 1);
            var registry = SelectionFixtures.registry(access);
            var handler = new LobbyPacketHandler(
                new EnterLobbyAction(
                    registry
                ),
                new LeaveLobbyAction(
                    registry
                ),
                access
            );
            var output = new CollectingPacketOutput();
            var calls = new AtomicInteger();
            access.onAccess = () -> {
                if (calls.incrementAndGet() == 2) {
                    access.players.clear();
                }
            };
            byte[] request = entering ? ChannelFixtures.enter(1) : ChannelFixtures.leave();

            assertEquals(LobbyHandlingOutcome.IGNORED, handler.handle(id, request, entering, output).outcome());
            assertEquals(LobbyHandlingOutcome.IGNORED, handler.handle(id, new byte[0], entering, output).outcome());
            assertTrue(output.batches.isEmpty());
        }
    }
}
