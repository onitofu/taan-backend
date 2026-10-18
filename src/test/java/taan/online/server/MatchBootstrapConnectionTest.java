package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static taan.online.server.RoomConnectionWorld.read;
import static taan.online.server.RoomConnectionWorld.drain;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.SelectionFixtures;
import taan.online.RuntimeRoomTestAccess;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.LoadingDoneCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.StartGameCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.packet.LoadingPosition;
import taan.online.match.protocol.packet.PcGamePlayLoadingDone;
import taan.online.match.protocol.packet.PcGameSessionStartGame;
import taan.online.match.protocol.packet.PcGamePlaySetPos;
import taan.online.room.protocol.codec.RoomResponseCodec;

class MatchBootstrapConnectionTest {
    @Test
    void deliversGameplayPreludeToBothSessionsAndAcceptsTheirBootstrapTrafficInOrder() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var clients = prepare(world);
            var owner = clients.getFirst();
            var guest = clients.get(1);
            world.request(owner, RoomFixtures.root("start-empty"));
            var start = start(owner);
            assertEquals(start, start(guest));
            world.request(owner, MatchFixtures.root("native-policy-v4-report-enemies-0"));
            world.request(guest, MatchFixtures.root("native-policy-v4-report-enemies-1"));
            var first = start.players().getFirst();
            var second = start.players().get(1);
            world.request(owner, loading(first.slotIndex(), 336, 676));
            world.request(owner, loading(first.slotIndex(), 336, 676));

            assertNull(owner.channel().readOutbound());
            assertNull(guest.channel().readOutbound());
            world.request(guest, loading(second.slotIndex(), 1196, 669));
            var ownerPose = new SetPosCodec().decode(read(owner));
            var guestPose = new SetPosCodec().decode(read(guest));
            var ownerTurn = new TurnStartCodec().decode(read(owner));
            var guestTurn = new TurnStartCodec().decode(read(guest));

            assertEquals(second.slotIndex(), ownerPose.slotIndex());
            assertEquals(1196, ownerPose.x());
            assertEquals(first.slotIndex(), guestPose.slotIndex());
            assertEquals(336, guestPose.x());
            assertEquals(ownerTurn, guestTurn);
            assertEquals(0, ownerTurn.energyGrant());
            assertEquals(0, ownerTurn.windRawTenths());
            assertEquals(0, ownerTurn.boxAttemptBudget());
            assertEquals(100, ownerTurn.pickerBase());
            world.request(
                owner,
                new SetPosCodec().encode(new PcGamePlaySetPos(first.slotIndex(), 336, 676, first.facing()))
            );
            world.request(guest, loading(second.slotIndex(), 1196, 669));
            world.request(owner, new byte[]{0x6d, 0x08, 0x0b, 0x5e, 0, 0});
            world.request(owner, RoomFixtures.root("start-empty"));

            assertNull(owner.channel().readOutbound());
            assertNull(guest.channel().readOutbound());
            var room = RuntimeRoomTestAccess.room(world.rooms, 1);
            assertEquals(
                MatchPhase.PREVIEW,
                world.matches.current(world.rooms.matchContext(room.ownerSessionId()).token())
                    .orElseThrow().phase()
            );
            world.request(owner, SelectionFixtures.cash(0, 0));
            assertEquals(SelectionFixtures.cash(0, 0).length, read(owner).length);
        }
    }

    @Test
    void closesLoadingPeersWhenTheOnlyLocalSlotContractIsViolated() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var clients = prepare(world);
            world.request(clients.getFirst(), RoomFixtures.root("start-empty"));
            clients.forEach(RoomConnectionWorld::drain);
            world.request(clients.getFirst(), new LoadingDoneCodec().encode(new PcGamePlayLoadingDone(List.of())));
            world.tick();

            assertTrue(clients.stream().noneMatch(client -> client.channel().isActive()));
            assertTrue(world.rooms.forChannel(1).isEmpty());
        }
    }

    @Test
    void abortsThePreviewForKnownGameplayInsteadOfRelayingUntrustedActions() throws Exception {
        for (int type : new int[]{0x2c3c05b9, 0x470b0762, 0x3f8506de, 0x46de0756}) {
            try (var world = new RoomConnectionWorld()) {
                var clients = prepare(world);
                world.request(clients.getFirst(), RoomFixtures.root("start-empty"));
                clients.forEach(RoomConnectionWorld::drain);
                world.request(clients.getFirst(), loading(0, 97, 685));
                world.request(clients.get(1), loading(2, 1110, 685));
                clients.forEach(RoomConnectionWorld::drain);
                int size = switch (type) {
                    case 0x2c3c05b9 -> 14;
                    case 0x470b0762 -> 18;
                    case 0x3f8506de -> 11;
                    default -> 4;
                };
                byte[] root = new byte[size];

                for (int index = 0; index < 4; index++) {
                    root[index] = (byte) (type >>> (8 * index));
                }

                world.request(clients.getFirst(), root);

                for (var client : clients) {
                    assertEquals(406, read(client).length);
                    assertFalse(new RoomResponseCodec().ready(read(client)).ready());
                    assertTrue(client.channel().isActive());
                    assertNull(client.channel().readOutbound());
                }
            }
        }
    }

    private List<ConnectionContextTestHarness> prepare(RoomConnectionWorld world) throws Exception {
        var owner = world.client(1);
        var guest = world.client(2);
        drain(owner);
        int id = world.create(owner);
        drain(guest);
        world.join(guest, id);
        drain(owner);
        world.request(guest, RoomScenario.ready(1));
        drain(owner);
        drain(guest);
        return List.of(owner, guest);
    }

    private PcGameSessionStartGame start(ConnectionContextTestHarness client) {
        var codec = new RoomResponseCodec();
        assertEquals(0, codec.slot(read(client)).slot().slotIndex());
        assertEquals(2, codec.slot(read(client)).slot().slotIndex());
        return new StartGameCodec().decode(read(client));
    }

    private byte[] loading(int slot, int x, int y) {
        return new LoadingDoneCodec().encode(new PcGamePlayLoadingDone(List.of(new LoadingPosition(slot, x, y))));
    }
}
