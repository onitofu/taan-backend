package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.match.protocol.codec.DeathByDropCodec;
import taan.online.match.protocol.codec.EatItemBoxCodec;
import taan.online.match.protocol.codec.LoadingDoneCodec;
import taan.online.match.protocol.codec.MakeGhostCodec;
import taan.online.match.protocol.codec.MakeGhostRequestCodec;
import taan.online.match.protocol.codec.NotifyEatItemCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.SetGhostPosCodec;
import taan.online.match.protocol.codec.SetGhostTargetCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.LoadingPosition;
import taan.online.match.protocol.packet.PcGamePlayDeathByDrop;
import taan.online.match.protocol.packet.PcGamePlayEatItemBox;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PcGamePlayLoadingDone;
import taan.online.match.protocol.packet.PcGamePlayMakeGhost;
import taan.online.match.protocol.packet.PcGamePlayNotifyEatItem;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.PcGamePlaySetGhostPos;
import taan.online.match.protocol.packet.PcGamePlaySetGhostTarget;
import taan.online.match.protocol.packet.PcGamePlayTurnStart;
import taan.online.match.protocol.packet.PcGameSessionStartGame;
import taan.online.match.protocol.packet.PqGamePlayMakeGhost;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioIntPoint;
import taan.online.match.protocol.packet.ScenarioMakeItemBoxCommand;
import taan.online.protocol.BinaryWriter;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PcGameSessionInit;
import taan.online.room.protocol.packet.PcGameSessionReady;
import taan.online.session.protocol.packet.PcUpdateCash;

class MatchGhostIntegrationTest {
    @Test
    void createsMovesAndPicksUpThroughFourRealTcpConnectionsWithoutLosingTheSession() throws Exception {
        try (var database = new CharacterTestDatabase()) {
            database.reset();
            database.execute("UPDATE channels SET kind = 0");
            var accounts = new ArrayList<Account>();

            for (int index = 0; index < 4; index++) {
                var account = database.account("ghost" + index + "@example.test", AccountGender.FEMALE);
                database.create(account.id(), "GhostHero" + index, AccountGender.FEMALE);
                accounts.add(account);
            }

            var config = database.configuration();

            try (var app = new Application(config)) {
                app.start();

                try (
                    var a = new CharacterTestClient(config.port());
                    var b = new CharacterTestClient(config.port());
                    var c = new CharacterTestClient(config.port());
                    var d = new CharacterTestClient(config.port())
                ) {
                    var clients = List.of(a, b, c, d);

                    for (int index = 0; index < clients.size(); index++) {
                        RoomIntegrationSupport.enterLobby(database, clients.get(index), accounts.get(index), 1);
                    }

                    a.send(RoomFixtures.root("create-unprotected"));
                    int room = new RoomResponseCodec().initialize(until(a, PcGameSessionInit.TYPE_ID)).info().roomId();

                    for (int index = 1; index < clients.size(); index++) {
                        var client = clients.get(index);
                        client.send(RoomEntryFixtures.enter(room));
                        until(client, PcGameSessionInit.TYPE_ID);
                        client.send(RoomScenario.ready(1));
                        long account = accounts.get(index).id().value();
                        var codec = new RoomResponseCodec();

                        while (true) {
                            var ready = codec.ready(until(client, PcGameSessionReady.TYPE_ID));

                            if (ready.accountId() == account && ready.ready()) {
                                break;
                            }
                        }
                    }

                    a.send(RoomFixtures.root("start-empty"));

                    for (var client : clients) {
                        until(client, PcGameSessionStartGame.TYPE_ID);
                    }

                    var loading = RuntimeRoomTestAccess.matchSnapshot(app, room);
                    Map<UUID, CharacterTestClient> sessions = new HashMap<>();

                    for (var member : loading.participants()) {
                        int index = accounts.stream().map(account -> account.id())
                            .toList().indexOf(member.character().accountId());
                        var client = clients.get(index);
                        sessions.put(member.sessionId(), client);
                        client.send(new LoadingDoneCodec().encode(new PcGamePlayLoadingDone(List.of(
                            new LoadingPosition(
                                member.slotIndex(),
                                member.spawn().settledX(),
                                member.spawn().settledY()
                            )
                        ))));
                    }

                    for (var client : clients) {
                        until(client, PcGamePlayTurnStart.TYPE_ID);
                    }

                    RuntimeRoomTestAccess.awaitMatchAction(app, room);
                    var initial = RuntimeRoomTestAccess.matchSnapshot(app, room);
                    var shooter = initial.participants().stream()
                        .filter(member -> member.slotIndex() == initial.turn().activeSlot()).findFirst().orElseThrow();
                    var victim = initial.participants().stream().filter(member -> member.team() != shooter.team())
                        .findFirst().orElseThrow();
                    var position = initial.actors().get(victim.sessionId());
                    var source = TeamScenarioPackets.shot(initial, List.of(), victim.slotIndex(), 10000);
                    var commands = new ArrayList<ScenarioCommand>(source.scenario().commands());
                    long box = 0x0500000000000001L;
                    commands.add(new ScenarioMakeItemBoxCommand(
                        new ScenarioCommandHeader(shooter.slotIndex(), 10, 500, 0),
                        box,
                        new ScenarioIntPoint(position.x(), position.y())
                    ));
                    commands.sort(Comparator.comparingInt(command -> command.header().tick()));
                    var scene = source.scenario();
                    var packet = new PcGamePlayScenario(
                        source.facing(),
                        source.shooterSlot(),
                        new GameScenario(
                            scene.slot(),
                            scene.scalar09(),
                            commands,
                            scene.selectedItems(),
                            scene.effects(),
                            scene.flags(),
                            scene.power(),
                            scene.refittedSlots(),
                            scene.scalar0c()
                        ),
                        source.scalar18(),
                        source.scalar1c(),
                        source.tail()
                    );
                    byte[] shot = new ScenarioCodec().encode(packet);
                    var shootingClient = sessions.get(shooter.sessionId());
                    shootingClient.sendFragmented(shot);

                    for (var client : clients) {
                        if (client != shootingClient) {
                            assertArrayEquals(shot, until(client, PcGamePlayScenario.TYPE_ID));
                        }
                    }

                    var dead = sessions.get(victim.sessionId());
                    dead.send(new MakeGhostRequestCodec().encode(new PqGamePlayMakeGhost(victim.slotIndex())));

                    for (var client : clients) {
                        assertEquals(victim.slotIndex(), new MakeGhostCodec().decode(
                            until(client, PcGamePlayMakeGhost.TYPE_ID)
                        ).slotIndex());
                    }

                    int targetX = position.x() > 650 ? position.x() - 150 : position.x() + 150;
                    var target = new PcGamePlaySetGhostTarget(victim.slotIndex(), targetX, position.y());
                    dead.send(new SetGhostTargetCodec().encode(target));

                    for (var client : clients) {
                        assertEquals(target, new SetGhostTargetCodec().decode(
                            until(client, PcGamePlaySetGhostTarget.TYPE_ID)
                        ));
                    }

                    byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
                        .unsignedByte(shooter.slotIndex()).unsignedByte(0).toByteArray();

                    for (var client : clients) {
                        client.send(ack);
                    }

                    for (var client : clients) {
                        var next = new TurnStartCodec().decode(until(client, PcGamePlayTurnStart.TYPE_ID));
                        assertEquals(victim.slotIndex(), next.ghostMoves().getFirst().slotIndex());
                    }

                    var pickup = new PcGamePlayEatItemBox(
                        victim.slotIndex(),
                        new ScenarioEatItemBoxCommand(
                            new ScenarioCommandHeader(victim.slotIndex(), 11, -30, 0),
                            box,
                            1,
                            0
                        ),
                        Optional.empty()
                    );
                    byte[] root = new EatItemBoxCodec().encode(pickup);
                    dead.sendFragmented(root);
                    dead.sendTogether(root, new NotifyEatItemCodec().encode(
                        new PcGamePlayNotifyEatItem(victim.slotIndex(), 0, 1)
                    ));

                    for (var client : clients) {
                        assertArrayEquals(root, until(client, PcGamePlayEatItemBox.TYPE_ID));
                    }

                    var notification = new PcGamePlayDeathByDrop(victim.slotIndex(), position.x(), position.y());
                    dead.send(new DeathByDropCodec().encode(notification));
                    var victimAccount = accounts.stream().filter(
                        account -> account.id().equals(victim.character().accountId())
                    ).findFirst().orElseThrow();
                    RoomIntegrationSupport.assertBalances(database, dead, victimAccount);
                    var after = RuntimeRoomTestAccess.matchSnapshot(app, room);
                    assertFalse(after.world().boxes().get(box).active());
                    assertEquals(0, after.actors().get(victim.sessionId()).hp());
                    assertEquals(1, after.loot().inventory(victim.sessionId()).receipts().size());
                    assertTrue(after.loot().inventory(victim.sessionId()).receipts().getFirst().confirmed());
                    assertTrue(after.resolution().pendingOutcome().isEmpty());
                    var confirmation = new PcGamePlaySetGhostPos(
                        victim.slotIndex(),
                        position.x(),
                        position.y(),
                        position.facing()
                    );
                    dead.sendTogether(new SetGhostPosCodec().encode(confirmation), SelectionFixtures.cash(0, 0));
                    assertArrayEquals(SelectionFixtures.cash(0, 0), until(dead, PcUpdateCash.TYPE_ID));
                    assertArrayEquals(SelectionFixtures.money(0), dead.read());
                }
            }
        }
    }

    private byte[] until(CharacterTestClient client, int type) throws Exception {
        for (int count = 0; count < 64; count++) {
            byte[] root = client.read();

            if (ChannelFixtures.type(root) == type) {
                return root;
            }
        }

        throw new AssertionError("Expected packet type was not received");
    }
}
