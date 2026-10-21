package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.read;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import taan.online.ChannelFixtures;
import taan.online.MatchFixtures;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.RuntimeRoomTestAccess;
import taan.online.channel.model.ChannelDefinition;
import taan.online.channel.model.ChannelGroupDefinition;
import taan.online.match.model.MatchBoxPolicy;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.EatBonusItemCodec;
import taan.online.match.protocol.codec.NotifyEatItemCodec;
import taan.online.match.protocol.codec.PassTurnCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.TurnEndCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PcGamePlayNotifyEatItem;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.protocol.BinaryWriter;
import taan.online.room.service.model.RoomMatchToken;

final class ChannelLootConnectionFixture implements AutoCloseable {
    final RoomConnectionWorld world;
    final List<ConnectionContextTestHarness> clients = new ArrayList<>();

    ChannelLootConnectionFixture(int firstScalar, int secondScalar, int count) {
        world = new RoomConnectionWorld(
            MatchExecutionMode.CLIENT_SCENARIO,
            MatchLootPolicy.nativeItems(),
            MatchBoxPolicy.ENABLED,
            List.of(new ChannelGroupDefinition(
                1,
                "Solar",
                0,
                List.of(
                    new ChannelDefinition(1, "Moon", true, 1, 109, 100, 0, firstScalar),
                    new ChannelDefinition(2, "Terra", true, 1, 109, 100, 0, secondScalar)
                )
            ))
        );

        for (int account = 1; account <= count; account++) {
            var client = world.client(account);
            clients.add(client);

            if (account > 2) {
                world.request(client, ChannelFixtures.move(2));
                world.request(client, ChannelFixtures.enter(1));
            }

            world.drainOutputs();
        }
    }

    RoomMatchToken start(int firstAccount, boolean captain, int weapon, int expectedScalar) throws Exception {
        var owner = clients.get(firstAccount - 1);
        var guest = clients.get(firstAccount);
        int room = world.create(owner);
        world.drainOutputs();
        world.join(guest, room);
        world.request(owner, RoomScenario.weapon(1000 + firstAccount, weapon));
        world.request(guest, RoomScenario.weapon(1001 + firstAccount, weapon));
        world.request(owner, RoomScenario.settings(1, captain ? 2 : 0, 2, 0));
        world.request(guest, RoomScenario.ready(1));

        if (captain) {
            world.request(owner, RoomFixtures.root("PqGameSession_ChiefDecision-native"));
            world.timers.advanceSeconds(5);
        }

        world.drainOutputs();
        world.request(owner, RoomFixtures.root("start-empty"));
        var id = RuntimeRoomTestAccess.room(world.rooms, room).ownerSessionId();
        var token = world.rooms.matchContext(id).token();
        world.drainOutputs();
        MatchFixtures.loaded(world.matches, token);
        world.tick();
        assertEquals(expectedScalar, snapshot(token).turn().pickerBase());

        for (var client : List.of(owner, guest)) {
            new SetPosCodec().decode(read(client));
            var turn = new TurnStartCodec().decode(read(client));
            assertEquals(expectedScalar, turn.pickerBase());
            assertEquals(0, turn.pickerTeam0());
            assertEquals(0, turn.pickerTeam1());
            assertEquals(0, turn.pickerTeam2());
            assertTrue(client.channel().outboundMessages().isEmpty());
        }

        return token;
    }

    MatchSnapshot snapshot(RoomMatchToken token) {
        return world.matches.current(token).orElseThrow();
    }

    MatchParticipant active(RoomMatchToken token) {
        var snapshot = snapshot(token);
        return snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
            .findFirst().orElseThrow();
    }

    ConnectionContextTestHarness client(MatchParticipant member) {
        return clients.get((int) member.character().accountId().value() - 1);
    }

    void pass(RoomMatchToken token) {
        var snapshot = snapshot(token);
        var member = active(token);
        var actor = snapshot.actors().get(member.sessionId());
        world.request(client(member), new PassTurnCodec().encode(new PcGamePlayPassTurn(
            member.slotIndex(),
            actor.x(),
            actor.y(),
            actor.facing(),
            0
        )));
        assertEquals(snapshot.turn().revision() + 1, snapshot(token).turn().revision());
        assertEquals(snapshot.turn().pickerBase(), snapshot(token).turn().pickerBase());
        world.drainOutputs();
    }

    byte[] bonus(MatchParticipant member, int item) {
        return new EatBonusItemCodec().encode(new PcGamePlayEatBonusItem(
            member.slotIndex(),
            new ScenarioEatItemBoxCommand(new ScenarioCommandHeader(member.slotIndex(), 11, -17, -3), -1L, item, -1),
            Optional.empty()
        ));
    }

    void confirm(MatchParticipant member, int index, int item) {
        world.request(client(member), new NotifyEatItemCodec().encode(
            new PcGamePlayNotifyEatItem(member.slotIndex(), index, item)
        ));
    }

    void expectBoth(RoomMatchToken token, byte[] expected) {
        snapshot(token).participants().forEach(member -> assertArrayEquals(expected, read(client(member))));
    }

    void empty(RoomMatchToken token) {
        snapshot(token).participants().forEach(member -> assertTrue(
            client(member).channel().outboundMessages().isEmpty()
        ));
    }

    void ack(RoomMatchToken token) {
        var before = snapshot(token);
        var shooter = active(token);
        byte[] root = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
            .unsignedByte(shooter.slotIndex()).unsignedByte(0).toByteArray();
        world.request(client(shooter), root);
        world.request(client(shooter), root);
        assertEquals(1, snapshot(token).completion().orElseThrow().awaitingSessions().size());

        before.participants().stream().filter(p -> !p.sessionId().equals(shooter.sessionId()))
            .forEach(p -> world.request(client(p), root));
        assertEquals(MatchPhase.ACTION, snapshot(token).phase());
        assertEquals(before.turn().revision() + 1, snapshot(token).turn().revision());

        for (var member : before.participants()) {
            var connection = client(member);
            new TurnEndCodec().decode(read(connection));
            assertEquals(before.turn().pickerBase(), new TurnStartCodec().decode(read(connection)).pickerBase());
            assertTrue(connection.channel().isActive());
            assertTrue(connection.channel().outboundMessages().isEmpty());
        }
    }

    @Override
    public void close() {
        world.close();
    }
}
