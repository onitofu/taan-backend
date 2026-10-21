package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.read;

import io.netty.buffer.Unpooled;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.MatchScenarioTestPackets;
import taan.online.MatchSpecialTestPackets;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.RuntimeRoomTestAccess;
import taan.online.SelectionFixtures;
import taan.online.match.protocol.codec.PassTurnCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.SpendItemCodec;
import taan.online.match.protocol.codec.TurnEndCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.codec.UseItemCodec;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.network.FrameDecoder;
import taan.online.protocol.BinaryWriter;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.RoomMatchToken;

class MatchItemConnectionTest {
    @Test
    void routesTwoChargedSpecialsThroughTheRealConnectionDispatcherAndKeepsNativeBonusReportsHarmless()
        throws Exception {
        try (var world = new RoomConnectionWorld(MatchExecutionMode.CLIENT_SCENARIO)) {
            var owner = world.client(1);
            var guest = world.client(2);
            world.drainOutputs();
            int room = world.create(owner);
            world.drainOutputs();
            world.join(guest, room);
            world.request(guest, RoomScenario.ready(1));
            world.request(owner, RoomFixtures.root("start-empty"));
            var token = world.rooms.matchContext(RuntimeRoomTestAccess.room(
                world.rooms,
                room
            ).ownerSessionId()).token();
            MatchFixtures.loaded(world.matches, token);
            world.drainOutputs();

            for (int use = 0; use < 2; use++) {
                for (int turn = 0; turn < 20; turn++) {
                    pass(world, token, owner, guest);
                }

                var snapshot = world.matches.current(token).orElseThrow();
                var member = snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
                    .findFirst().orElseThrow();
                var actor = snapshot.actors().get(member.sessionId());
                var active = member.slotIndex() == 0 ? owner : guest;
                var observer = member.slotIndex() == 0 ? guest : owner;
                int team = member.team() == RoomTeam.BLUE ? 1 : 2;
                assertEquals(100, actor.energy());
                byte[] activation = new UseItemCodec().encode(new PcGamePlayUseItem(
                    member.slotIndex(),
                    team,
                    0,
                    0,
                    255,
                    6
                ));
                byte[] spend = new SpendItemCodec().encode(new PcGamePlaySpendItem(
                    member.slotIndex(),
                    team,
                    255,
                    List.of()
                ));
                byte[] scenario = new ScenarioCodec().encode(MatchSpecialTestPackets.special(
                    MatchScenarioTestPackets.shot(member.slotIndex(), actor.facing(), 0, false)
                ));
                byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
                    .unsignedByte(member.slotIndex()).unsignedByte(0).toByteArray();
                active.channel().pipeline().addFirst(new FrameDecoder(16384, 10));
                var combined = Unpooled.buffer();

                try {
                    for (byte[] root : new byte[][]{activation, spend, scenario, ack}) {
                        combined.writeIntLE(root.length).writeBytes(root);
                    }

                    while (combined.isReadable()) {
                        int size = use == 0 ? Math.min(7, combined.readableBytes()) : combined.readableBytes();
                        active.channel().writeInbound(combined.readRetainedSlice(size));
                    }
                } finally {
                    combined.release();
                }

                while (!active.jobs().isEmpty()) {
                    active.run();
                    world.tick();
                }
                active.channel().pipeline().remove(FrameDecoder.class);
                assertArrayEquals(activation, read(active));
                assertArrayEquals(activation, read(observer));
                assertArrayEquals(spend, read(active));
                assertArrayEquals(spend, read(observer));
                assertArrayEquals(scenario, read(observer));
                world.request(observer, ack);
                new TurnEndCodec().decode(read(owner));
                new TurnEndCodec().decode(read(guest));
                assertEquals(new TurnStartCodec().decode(read(owner)), new TurnStartCodec().decode(read(guest)));
                assertTrue(owner.channel().isActive());
                assertTrue(guest.channel().isActive());
                assertEquals(0, world.matches.current(token).orElseThrow().actors().get(member.sessionId()).energy());

                byte[] bonus = MatchFixtures.root("pending-03-PcGamePlay_EatBonusItem");
                bonus[4] = (byte) member.slotIndex();
                bonus[9] = (byte) member.slotIndex();
                var before = world.matches.current(token).orElseThrow();
                world.request(active, bonus);
                assertTrue(active.channel().outboundMessages().isEmpty());
                assertTrue(observer.channel().outboundMessages().isEmpty());
                world.request(active, SelectionFixtures.cash(0, 0));
                assertArrayEquals(SelectionFixtures.cash(0, 0), read(active));
                assertArrayEquals(SelectionFixtures.money(0), read(active));
                assertEquals(before, world.matches.current(token).orElseThrow());
            }
        }
    }

    private void pass(
        RoomConnectionWorld world,
        RoomMatchToken token,
        ConnectionContextTestHarness owner,
        ConnectionContextTestHarness guest
    ) {
        var snapshot = world.matches.current(token).orElseThrow();
        var member = snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
            .findFirst().orElseThrow();
        var actor = snapshot.actors().get(member.sessionId());
        var active = member.slotIndex() == 0 ? owner : guest;
        var observer = member.slotIndex() == 0 ? guest : owner;
        var pass = new PcGamePlayPassTurn(member.slotIndex(), actor.x(), actor.y(), actor.facing(), 0);
        world.request(active, new PassTurnCodec().encode(pass));
        assertEquals(pass, new PassTurnCodec().decode(read(observer)));
        new SetPosCodec().decode(read(observer));
        new TurnEndCodec().decode(read(owner));
        new TurnEndCodec().decode(read(guest));
        assertEquals(new TurnStartCodec().decode(read(owner)), new TurnStartCodec().decode(read(guest)));
    }
}
