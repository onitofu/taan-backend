package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.read;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.MatchScenarioTestPackets;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.RuntimeRoomTestAccess;
import taan.online.SelectionFixtures;
import taan.online.TestFixtures;
import taan.online.match.protocol.codec.EndGameCodec;
import taan.online.match.protocol.codec.MakeGhostRequestCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.PqGamePlayMakeGhost;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.protocol.BinaryWriter;
import taan.online.room.protocol.codec.RoomResponseCodec;

class MatchGhostConnectionTest {
    @Test
    void keepsBothPhysicalLoserSlotsConnectedBeforeAckAfterEndGameAndDuringARematch() throws Exception {
        for (boolean suicide : new boolean[]{false, true}) {
            try (var world = new RoomConnectionWorld(MatchExecutionMode.CANNON_1V1); var logs = new LogCapture()) {
                var owner = world.client(1);
                var guest = world.client(2);
                int room = prepare(world, owner, guest);
                var roomOwner = RuntimeRoomTestAccess.room(world.rooms, room).ownerSessionId();
                var token = world.rooms.matchContext(roomOwner).token();
                MatchFixtures.loaded(world.matches, token);
                var before = world.matches.current(token).orElseThrow();
                int slot = before.turn().activeSlot();
                var shooter = slot == 0 ? owner : guest;
                var observer = slot == 0 ? guest : owner;
                var member = before.participants().stream().filter(p -> p.slotIndex() == slot)
                    .findFirst().orElseThrow();
                var actor = before.actors().get(member.sessionId());
                var packet = shot(slot, actor.facing(), suicide);
                byte[] root = new ScenarioCodec().encode(packet);
                int deadSlot = suicide ? slot : slot == 0 ? 2 : 0;
                var dead = deadSlot == 0 ? owner : guest;
                byte[] ghost = new MakeGhostRequestCodec().encode(new PqGamePlayMakeGhost(deadSlot));
                world.drainOutputs();

                world.request(shooter, root);
                assertArrayEquals(root, read(observer));
                var committed = world.matches.current(token).orElseThrow();
                world.request(dead, ghost);
                world.request(dead, ghost);
                assertSame(committed, world.matches.current(token).orElseThrow());
                assertTrue(owner.channel().isActive());
                assertTrue(guest.channel().isActive());
                assertTrue(owner.channel().outboundMessages().isEmpty());
                assertTrue(guest.channel().outboundMessages().isEmpty());
                byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
                    .unsignedByte(slot).unsignedByte(0).toByteArray();
                world.request(shooter, ack);
                assertTrue(world.matches.current(token).isPresent());
                world.request(observer, ack);
                var ownerResult = new EndGameCodec().decode(read(owner));
                assertEquals(ownerResult, new EndGameCodec().decode(read(guest)));
                assertFalse(new RoomResponseCodec().ready(read(owner)).ready());
                assertFalse(new RoomResponseCodec().ready(read(guest)).ready());
                assertTrue(world.matches.current(token).isEmpty());

                world.request(dead, ghost);
                world.request(dead, SelectionFixtures.cash(0, 0));
                assertArrayEquals(SelectionFixtures.cash(0, 0), read(dead));
                assertTrue(owner.channel().isActive());
                assertTrue(guest.channel().isActive());
                assertTrue(owner.channel().outboundMessages().isEmpty());
                assertTrue(guest.channel().outboundMessages().isEmpty());

                world.request(guest, RoomScenario.ready(1));
                world.request(owner, RoomFixtures.root("start-empty"));
                var nextToken = world.rooms.matchContext(roomOwner).token();
                MatchFixtures.loaded(world.matches, nextToken);
                world.drainOutputs();
                var fresh = world.matches.current(nextToken).orElseThrow();
                world.request(dead, ghost);
                assertSame(fresh, world.matches.current(nextToken).orElseThrow());
                assertTrue(owner.channel().outboundMessages().isEmpty());
                assertTrue(guest.channel().outboundMessages().isEmpty());
                assertTrue(logs.text().contains("match.ghost.request.ignored"));
                assertFalse(logs.text().contains(TestFixtures.LOGIN));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            }
        }
    }

    @Test
    void acceptsAWellFormedRequestInTheWaitingRoomButRejectsTruncatedAndInvalidSlotRequests() throws Exception {
        for (byte[] malformed : List.of(
            new BinaryWriter().unsignedInt(PqGamePlayMakeGhost.TYPE_ID).toByteArray(),
            new BinaryWriter().unsignedInt(PqGamePlayMakeGhost.TYPE_ID).unsignedByte(8).toByteArray()
        )) {
            try (var world = new RoomConnectionWorld(MatchExecutionMode.CANNON_1V1)) {
                var owner = world.client(1);
                world.create(owner);
                world.drainOutputs();
                var ghost = new MakeGhostRequestCodec().encode(new PqGamePlayMakeGhost(0));
                world.request(owner, ghost);
                assertTrue(owner.channel().isActive());
                assertTrue(owner.channel().outboundMessages().isEmpty());

                world.request(owner, malformed);
                assertFalse(owner.channel().isActive());
            }
        }
    }

    private int prepare(
        RoomConnectionWorld world,
        ConnectionContextTestHarness owner,
        ConnectionContextTestHarness guest
    ) throws Exception {
        world.drainOutputs();
        int room = world.create(owner);
        world.drainOutputs();
        world.join(guest, room);
        world.request(guest, RoomScenario.ready(1));
        world.request(owner, RoomFixtures.root("start-empty"));
        return room;
    }

    private PcGamePlayScenario shot(int slot, int facing, boolean suicide) throws Exception {
        var source = MatchScenarioTestPackets.shot(slot, facing, 10000, false);
        var s = source.scenario();
        var events = s.commands().stream().map(event -> {
            if (suicide && event instanceof ScenarioDamageCommand damage) {
                var h = damage.header();
                return new ScenarioDamageCommand(
                    new ScenarioCommandHeader(slot, h.kind(), h.tick(), h.scalar10()),
                    damage.damage(),
                    damage.simulationHp(),
                    damage.energy1c(),
                    damage.energy1e()
                );
            }

            return event;
        }).toList();
        return new PcGamePlayScenario(
            source.facing(),
            slot,
            new GameScenario(
                s.slot(),
                s.scalar09(),
                events,
                s.selectedItems(),
                s.effects(),
                s.flags(),
                s.power(),
                s.refittedSlots(),
                s.scalar0c()
            ),
            source.scalar18(),
            source.scalar1c(),
            source.tail()
        );
    }
}
