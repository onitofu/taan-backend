package taan.online.match.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.match.protocol.codec.DeathByDropCodec;
import taan.online.match.protocol.codec.SetGhostPosCodec;
import taan.online.match.protocol.codec.SetGhostTargetCodec;
import taan.online.match.protocol.packet.PcGamePlayDeathByDrop;
import taan.online.match.protocol.packet.PcGamePlaySetGhostPos;
import taan.online.match.protocol.packet.PcGamePlaySetGhostTarget;

class GhostHandlerTest {
    @Test
    void consumesRequestsWithoutBindingsAndRejectsForeignBindingsWithoutPublishing() {
        try (var f = new MatchItemHandlerFixture()) {
            byte[] target = new SetGhostTargetCodec().encode(new PcGamePlaySetGhostTarget(0, 100, 100));
            byte[] position = new SetGhostPosCodec().encode(new PcGamePlaySetGhostPos(0, 100, 100, 0));
            byte[] drop = new DeathByDropCodec().encode(new PcGamePlayDeathByDrop(0, 100, 1050));
            var request = f.handler.capture(f.room.owner);
            f.owner.batches.clear();
            f.guest.batches.clear();
            f.handle(f.room.owner, PcGamePlayDeathByDrop.TYPE_ID, drop, null);
            f.handle(UUID.randomUUID(), PcGamePlayDeathByDrop.TYPE_ID, drop, request);
            f.handle(f.room.owner, PcGamePlayDeathByDrop.TYPE_ID, drop, request);
            f.handle(f.room.owner, PcGamePlaySetGhostTarget.TYPE_ID, target, null);
            f.handle(f.room.owner, PcGamePlaySetGhostPos.TYPE_ID, position, null);
            f.handle(UUID.randomUUID(), PcGamePlaySetGhostTarget.TYPE_ID, target, request);
            f.handle(UUID.randomUUID(), PcGamePlaySetGhostPos.TYPE_ID, position, request);
            f.handle(f.room.owner, PcGamePlaySetGhostTarget.TYPE_ID, target, request);
            f.handle(f.room.owner, PcGamePlaySetGhostPos.TYPE_ID, position, request);
            assertTrue(f.owner.batches.isEmpty());
            assertTrue(f.guest.batches.isEmpty());
        }
    }
}
