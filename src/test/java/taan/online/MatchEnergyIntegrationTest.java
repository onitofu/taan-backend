package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.EatBonusItemCodec;
import taan.online.match.protocol.codec.LoadingDoneCodec;
import taan.online.match.protocol.codec.NotifyEatItemCodec;
import taan.online.match.protocol.codec.PassTurnCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.SpendItemCodec;
import taan.online.match.protocol.codec.StartGameCodec;
import taan.online.match.protocol.codec.TurnEndCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.codec.UseItemCodec;
import taan.online.match.protocol.packet.LoadingPosition;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PcGamePlayLoadingDone;
import taan.online.match.protocol.packet.PcGamePlayNotifyEatItem;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.match.service.MatchRegistry;
import taan.online.protocol.BinaryWriter;
import taan.online.room.model.RoomTeam;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.service.model.RoomMatchToken;

class MatchEnergyIntegrationTest {
    private final RoomResponseCodec rooms = new RoomResponseCodec();

    @Test
    void chargesFromZeroAndReusesSpecialThroughCoalescedTcpPacketsWithoutPersistentRewards() throws Exception {
        try (var database = new CharacterTestDatabase()) {
            database.reset();
            database.execute("UPDATE channels SET kind = 0");
            var a = player(database, "alpha");
            var b = player(database, "bravo");
            var config = database.configuration();

            try (var app = new Application(config)) {
                app.start();

                try (
                    var owner = new CharacterTestClient(config.port());
                    var guest = new CharacterTestClient(config.port())
                ) {
                    lobby(database, owner, a);
                    lobby(database, guest, b);
                    owner.read();
                    owner.send(RoomFixtures.root("create-unprotected"));
                    int roomId = rooms.initialize(owner.read()).info().roomId();
                    guest.read();
                    guest.read();
                    guest.send(RoomEntryFixtures.enter(roomId));
                    rooms.initialize(guest.read());
                    rooms.slot(owner.read());
                    guest.send(RoomScenario.ready(1));
                    rooms.ready(owner.read());
                    rooms.ready(guest.read());
                    owner.send(RoomFixtures.root("start-empty"));
                    started(owner);
                    started(guest);
                    var roomRegistry = RuntimeRoomTestAccess.rooms(app);
                    var matches = RuntimeRoomTestAccess.matches(roomRegistry);
                    var token = roomRegistry.matchContext(RuntimeRoomTestAccess.room(
                        roomRegistry,
                        roomId
                    ).ownerSessionId()).token();
                    var roster = matches.current(token).orElseThrow().participants();
                    loading(owner, roster.getFirst());
                    loading(guest, roster.get(1));
                    new SetPosCodec().decode(owner.read());
                    new SetPosCodec().decode(guest.read());
                    assertEquals(new TurnStartCodec().decode(owner.read()), new TurnStartCodec().decode(guest.read()));
                    assertTrue(matches.current(token).orElseThrow().actors().values().stream()
                        .allMatch(p -> p.energy() == 0));

                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

                    while (matches.current(token).orElseThrow().phase() != MatchPhase.ACTION
                        && System.nanoTime() < deadline) {
                        Thread.sleep(1);
                    }

                    assertEquals(MatchPhase.ACTION, matches.current(token).orElseThrow().phase());
                    lootMessages(matches, token, owner, 0);
                    lootMessages(matches, token, guest, 2);

                    for (int use = 0; use < 2; use++) {
                        for (int pass = 0; pass < 20; pass++) {
                            pass(matches, token, owner, guest);
                        }
                        special(matches, token, owner, guest);
                        if (use == 0) {
                            pass(matches, token, owner, guest);
                        }
                    }

                    owner.send(MatchFixtures.root("give-up-empty"));
                    for (var client : List.of(owner, guest)) {
                        MatchResultAssertions.readEnd(client::read, 2);
                        rooms.ready(client.read());
                        client.send(SelectionFixtures.cash(0, 0));
                        assertArrayEquals(SelectionFixtures.cash(0, 0), client.read());
                        assertArrayEquals(SelectionFixtures.money(0), client.read());
                    }
                    assertTrue(matches.current(token).isEmpty());
                }
            }
        }
    }

    private void lootMessages(MatchRegistry matches, RoomMatchToken token, CharacterTestClient client, int slot)
        throws Exception {
        var before = matches.current(token).orElseThrow();
        byte[] pickup = MatchFixtures.root("EatItemBox-plain.synthetic");
        pickup[4] = (byte) slot;
        pickup[9] = (byte) slot;
        byte[] receipt = MatchFixtures.root("notify-slot0-power-actor" + slot + ".synthetic");
        byte[] discard = MatchFixtures.root("forsake-known-slot1-actor" + slot + ".synthetic");

        client.sendFragmented(pickup);
        client.sendTogether(receipt, discard, SelectionFixtures.cash(0, 0));
        assertArrayEquals(SelectionFixtures.cash(0, 0), client.read());
        assertArrayEquals(SelectionFixtures.money(0), client.read());
        assertEquals(before, matches.current(token).orElseThrow());
    }

    private void pass(MatchRegistry matches, RoomMatchToken token, CharacterTestClient owner, CharacterTestClient guest)
        throws Exception {
        var snapshot = matches.current(token).orElseThrow();
        var member = snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
            .findFirst().orElseThrow();
        var actor = snapshot.actors().get(member.sessionId());
        var active = member.slotIndex() == 0 ? owner : guest;
        var observer = member.slotIndex() == 0 ? guest : owner;
        var pass = new PcGamePlayPassTurn(member.slotIndex(), actor.x(), actor.y(), actor.facing(), 0);
        active.send(new PassTurnCodec().encode(pass));
        assertEquals(pass, new PassTurnCodec().decode(observer.read()));
        new SetPosCodec().decode(observer.read());
        new TurnEndCodec().decode(owner.read());
        new TurnEndCodec().decode(guest.read());
        assertEquals(new TurnStartCodec().decode(owner.read()), new TurnStartCodec().decode(guest.read()));

        var before = matches.current(token).orElseThrow();
        String fixture = member.slotIndex() == 0 ? "pending-03-PcGamePlay_EatBonusItem"
            : "pending-04-PcGamePlay_EatBonusItem";
        byte[] bonus = MatchFixtures.root(fixture);
        int item = new EatBonusItemCodec().decode(bonus).command().itemId();
        int index = before.loot().inventory(member.sessionId()).firstEmpty();
        active.sendTogether(bonus, SelectionFixtures.cash(0, 0));
        assertArrayEquals(bonus, active.read());
        assertArrayEquals(SelectionFixtures.cash(0, 0), active.read());
        assertArrayEquals(SelectionFixtures.money(0), active.read());
        assertArrayEquals(bonus, observer.read());
        var after = matches.current(token).orElseThrow();
        assertEquals(before.world(), after.world());
        assertEquals(before.loot().grantSequence() + (index < 0 ? 0 : 1), after.loot().grantSequence());

        if (index >= 0) {
            var receipt = new PcGamePlayNotifyEatItem(member.slotIndex(), index, item);
            active.sendTogether(new NotifyEatItemCodec().encode(receipt), SelectionFixtures.cash(0, 0));
            assertArrayEquals(SelectionFixtures.cash(0, 0), active.read());
            assertArrayEquals(SelectionFixtures.money(0), active.read());
            assertTrue(matches.current(token).orElseThrow().loot().inventory(member.sessionId()).receipts().stream()
                .anyMatch(value -> value.inventoryIndex() == index && value.itemId() == item && value.confirmed()));
        }

        var confirmed = matches.current(token).orElseThrow();
        active.sendTogether(bonus, SelectionFixtures.cash(0, 0));
        assertArrayEquals(SelectionFixtures.cash(0, 0), active.read());
        assertArrayEquals(SelectionFixtures.money(0), active.read());
        assertEquals(confirmed, matches.current(token).orElseThrow());
    }

    private void special(
        MatchRegistry matches,
        RoomMatchToken token,
        CharacterTestClient owner,
        CharacterTestClient guest
    )
        throws Exception {
        var snapshot = matches.current(token).orElseThrow();
        var member = snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
            .findFirst().orElseThrow();
        var active = member.slotIndex() == 0 ? owner : guest;
        var observer = member.slotIndex() == 0 ? guest : owner;
        var actor = snapshot.actors().get(member.sessionId());
        assertEquals(100, actor.energy());
        var use = new PcGamePlayUseItem(member.slotIndex(), member.team() == RoomTeam.BLUE ? 1 : 2, 0, 0, 255, 6);
        byte[] useRoot = new UseItemCodec().encode(use);
        byte[] spend = new SpendItemCodec().encode(new PcGamePlaySpendItem(
            member.slotIndex(),
            use.team(),
            255,
            List.of()
        ));
        var shot = MatchSpecialTestPackets.special(MatchScenarioTestPackets.shot(
            member.slotIndex(),
            actor.facing(),
            0,
            false
        ));
        byte[] root = new ScenarioCodec().encode(shot);
        byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
            .unsignedByte(member.slotIndex()).unsignedByte(0).toByteArray();
        active.sendTogether(useRoot, spend, root, ack);
        assertArrayEquals(useRoot, active.read());
        assertArrayEquals(useRoot, observer.read());
        assertArrayEquals(spend, active.read());
        assertArrayEquals(spend, observer.read());
        assertArrayEquals(root, observer.read());
        observer.sendTogether(ack, ack);
        new TurnEndCodec().decode(owner.read());
        new TurnEndCodec().decode(guest.read());
        assertEquals(new TurnStartCodec().decode(owner.read()), new TurnStartCodec().decode(guest.read()));
        assertEquals(0, matches.current(token).orElseThrow().actors().get(member.sessionId()).energy());
    }

    private Account player(CharacterTestDatabase database, String name) {
        var account = database.account(name + "@example.test", AccountGender.FEMALE);
        database.create(account.id(), "Hero" + name, AccountGender.FEMALE);
        return account;
    }

    private void lobby(CharacterTestDatabase database, CharacterTestClient client, Account account) throws Exception {
        client.login(account.login().value());
        var character = database.characters().findByAccount(account.id()).getFirst().id();
        client.send(CharacterFixtures.select(character.value()));
        client.read();
        client.read();
        client.send(ChannelFixtures.move(1));
        client.read();
        client.send(ChannelFixtures.enter(1));
        client.read();
        client.read();
        client.read();
    }

    private void started(CharacterTestClient client) throws Exception {
        rooms.slot(client.read());
        rooms.slot(client.read());
        assertTrue(new StartGameCodec().decode(client.read()).players().stream()
            .allMatch(p -> p.initialEnergyGrant() == 0));
    }

    private void loading(CharacterTestClient client, MatchParticipant member) throws Exception {
        client.send(new LoadingDoneCodec().encode(new PcGamePlayLoadingDone(List.of(new LoadingPosition(
            member.slotIndex(),
            member.spawn().settledX(),
            member.spawn().settledY()
        )))));
    }
}
