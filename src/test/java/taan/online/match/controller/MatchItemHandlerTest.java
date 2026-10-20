package taan.online.match.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.MatchScenarioTestPackets;
import taan.online.MatchSpecialTestPackets;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.SpendItemCodec;
import taan.online.match.protocol.codec.UseItemCodec;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.match.protocol.packet.SpentItem;
import taan.online.match.service.ActivateSpecialAction;
import taan.online.match.service.ConfirmMatchItemReceiptAction;
import taan.online.match.service.DiscardMatchItemAction;
import taan.online.match.service.RecordMatchSpendAction;
import taan.online.match.service.ReportMatchBonusAction;
import taan.online.match.service.ReportMatchPickupAction;
import taan.online.match.service.model.ActivateSpecialCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.protocol.BinaryWriter;
import taan.online.room.model.RoomTeam;

class MatchItemHandlerTest {
    @Test
    void echoesActivationAndCommitsSpendWithScenarioFromTheCapturedContext() throws Exception {
        try (var f = new MatchItemHandlerFixture()) {
            f.charge();
            var member = f.active();
            var id = member.sessionId();
            var old = f.handler.capture(id);
            int team = member.team() == RoomTeam.BLUE ? 1 : 2;
            var use = new PcGamePlayUseItem(member.slotIndex(), team, 0, 0, 255, 6);
            byte[] useRoot = new UseItemCodec().encode(use);
            int before = f.owner.batches.size();
            f.handle(id, PcGamePlayUseItem.TYPE_ID, useRoot, old);
            assertArrayEquals(useRoot, f.owner.batches.getLast().getFirst());
            assertArrayEquals(useRoot, f.guest.batches.getLast().getFirst());
            assertEquals(before + 1, f.owner.batches.size());
            f.handle(UUID.randomUUID(), PcGamePlayUseItem.TYPE_ID, useRoot, old);
            assertEquals(before + 1, f.owner.batches.size());
            var spend = new PcGamePlaySpendItem(member.slotIndex(), team, 255, List.of(new SpentItem(6, 77)));
            byte[] spendRoot = new SpendItemCodec().encode(spend);
            f.handle(id, PcGamePlaySpendItem.TYPE_ID, spendRoot, old);
            assertEquals(before + 1, f.owner.batches.size());
            var actor = f.snapshot().actors().get(id);
            var normal = MatchScenarioTestPackets.shot(member.slotIndex(), actor.facing(), 0, false);
            var special = MatchSpecialTestPackets.special(normal);
            byte[] root = new ScenarioCodec().encode(special);
            f.handle(id, PcGamePlayScenario.TYPE_ID, root, old);
            assertEquals(MatchPhase.COMPLETION, f.snapshot().phase());
            var shooterOutput = id.equals(f.room.owner) ? f.owner : f.guest;
            var observerOutput = id.equals(f.room.owner) ? f.guest : f.owner;
            assertArrayEquals(spendRoot, shooterOutput.batches.getLast().getFirst());
            assertArrayEquals(spendRoot, observerOutput.batches.get(observerOutput.batches.size() - 2).getFirst());
            assertArrayEquals(root, observerOutput.batches.getLast().getFirst());
            byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
                .unsignedByte(member.slotIndex()).unsignedByte(0).toByteArray();
            f.handle(id, PcGamePlayEndScenario.TYPE_ID, ack, old);
            var peer = f.snapshot().participants().stream().filter(p -> !p.sessionId().equals(id))
                .findFirst().orElseThrow();
            f.handle(peer.sessionId(), PcGamePlayEndScenario.TYPE_ID, ack, f.handler.capture(peer.sessionId()));
            assertEquals(MatchPhase.ACTION, f.snapshot().phase());
            assertEquals(0, f.snapshot().actors().get(id).energy());
            assertTrue(shooterOutput.closeReasons.isEmpty());
            assertTrue(observerOutput.closeReasons.isEmpty());
        }
    }

    @Test
    void ignoresMissingForeignAndNoLongerExistingMemberships() {
        try (var f = new MatchItemHandlerFixture()) {
            var matches = f.room.scenario.matches;
            var activation = new ActivateSpecialAction(matches);
            var spending = new RecordMatchSpendAction(matches);
            var direct = new MatchItemPacketHandler(
                activation,
                spending,
                new ReportMatchBonusAction(matches),
                new ReportMatchPickupAction(matches),
                new ConfirmMatchItemReceiptAction(matches),
                new DiscardMatchItemAction(matches)
            );
            var captured = f.handler.capture(f.active().sessionId());
            var command = MatchCommandContext.from(captured.roomContext());
            direct.handle(
                UUID.randomUUID(),
                PcGamePlayUseItem.TYPE_ID,
                new byte[0],
                captured
            ).toCompletableFuture().join();
            direct.handle(
                f.room.owner,
                PcGamePlayUseItem.TYPE_ID,
                new byte[0],
                new MatchRequestContext(null, 0, 0, Optional.empty())
            )
                .toCompletableFuture().join();
            f.room.scenario.rooms.leave(f.room.owner, f.token.roomId().value(), 0);
            assertFalse(activation.execute(new ActivateSpecialCommand(
                command,
                captured.turnRevision(),
                captured.phaseRevision(),
                0,
                1,
                0,
                0,
                255,
                6
            )).toCompletableFuture().join());
            assertFalse(spending.execute(new RecordMatchSpendCommand(
                command,
                captured.turnRevision(),
                captured.phaseRevision(),
                0,
                1,
                255,
                List.of()
            )).toCompletableFuture().join());
        }
    }
}
