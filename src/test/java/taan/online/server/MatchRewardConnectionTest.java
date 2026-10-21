package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.read;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import taan.online.FixedRewardRandom;
import taan.online.MatchFixtures;
import taan.online.MatchScenarioTestPackets;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.RuntimeRoomTestAccess;
import taan.online.TestLevels;
import taan.online.character.model.CharacterProgress;
import taan.online.character.protocol.codec.UpdateExpCodec;
import taan.online.character.protocol.codec.UpdateMoneyCodec;
import taan.online.match.model.MatchResult;
import taan.online.match.model.MatchRewardRules;
import taan.online.match.protocol.codec.EndGameCodec;
import taan.online.match.protocol.codec.ResultSelectionCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.SetRewardCodec;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PqResultRandSelect;
import taan.online.match.protocol.packet.PqResultSelectCard;
import taan.online.match.repository.MatchSettlement;
import taan.online.match.service.SettleMatchResultAction;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.protocol.BinaryWriter;
import taan.online.room.protocol.codec.RoomResponseCodec;

class MatchRewardConnectionTest {
    @Test
    void paysOnceAndRoutesContestedRepeatedAndForeignCardChoices() throws Exception {
        var calls = new AtomicInteger();
        var saved = new AtomicReference<MatchResult>();
        var rules = new MatchRewardRules(TestLevels.table());
        var action = new SettleMatchResultAction(
            input -> {
                calls.incrementAndGet();
                var stored = input.rewarded(input.participants().stream().map(p -> rules.calculate(
                    input,
                    p,
                    new CharacterProgress(900, 0, 0, 0),
                    123
                )).toList());
                saved.set(stored);
                return new MatchSettlement(stored, true);
            },
            Runnable::run,
            Clock.systemUTC(),
            new FixedRewardRandom(99)
        );

        try (var world = new RoomConnectionWorld(MatchExecutionMode.CLIENT_SCENARIO, action)) {
            var owner = world.client(1);
            var guest = world.client(2);
            world.drainOutputs();
            int room = world.create(owner);
            world.drainOutputs();
            world.join(guest, room);
            world.request(guest, RoomScenario.ready(1));
            world.request(owner, RoomFixtures.root("start-empty"));
            var roomOwner = RuntimeRoomTestAccess.room(world.rooms, room).ownerSessionId();
            var token = world.rooms.matchContext(roomOwner).token();
            MatchFixtures.loaded(world.matches, token);
            var snapshot = world.matches.current(token).orElseThrow();
            int slot = snapshot.turn().activeSlot();
            var shooter = snapshot.participants().stream().filter(p -> p.slotIndex() == slot).findFirst().orElseThrow();
            var sender = slot == 0 ? owner : guest;
            var observer = slot == 0 ? guest : owner;
            world.drainOutputs();
            world.matchTimers.now += 10000000000L;
            world.request(
                sender,
                new ScenarioCodec().encode(MatchScenarioTestPackets.shot(
                    slot,
                    snapshot.actors().get(shooter.sessionId()).facing(),
                    10000,
                    false
                ))
            );
            new ScenarioCodec().decode(read(observer));
            byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
                .unsignedByte(slot).unsignedByte(0).toByteArray();
            world.request(sender, ack);
            world.request(observer, ack);

            assertEquals(1, calls.get());
            assertTrue(world.matches.current(token).isEmpty());

            for (var client : new ConnectionContextTestHarness[]{owner, guest}) {
                var progress = new UpdateExpCodec().decode(read(client));
                var money = new UpdateMoneyCodec().decode(read(client));
                var cards = new SetRewardCodec().decode(read(client));
                var end = new EndGameCodec().decode(read(client));
                int clientSlot = client == owner ? 0 : 2;
                var member = saved.get().participants().stream().filter(p -> p.slotIndex() == clientSlot)
                    .findFirst().orElseThrow();

                assertEquals(member.experienceReward().after().progress().experience(), progress.currentExp());
                assertEquals(member.goldReward().after(), money.balance());
                assertEquals(member.card().amount(), cards.rewards().get(clientSlot).amount());
                assertEquals(member.goldReward().delta(), end.result().slots().get(clientSlot).displayedGold());
                assertEquals(clientSlot == slot ? 1 : 0, end.result().slots().get(clientSlot).accuracy());
                new RoomResponseCodec().ready(read(client));
                assertTrue(client.channel().outboundMessages().isEmpty());
            }

            var cards = new ResultSelectionCodec();
            world.request(owner, cards.encode(new PqResultSelectCard(3, 0)));
            assertEquals(3, cards.selected(read(owner)).cardIndex());
            assertEquals(0, cards.selected(read(guest)).slotIndex());
            world.request(guest, cards.encode(new PqResultSelectCard(3, 2)));
            assertEquals(3, cards.alreadyOpened(read(guest)).cardIndex());
            assertTrue(owner.channel().outboundMessages().isEmpty());
            world.request(guest, cards.encode(new PqResultRandSelect(2)));
            int chosen = cards.selected(read(guest)).cardIndex();
            assertTrue(chosen != 3);
            assertEquals(chosen, cards.selected(read(owner)).cardIndex());
            world.request(owner, cards.encode(new PqResultSelectCard(7, 0)));
            assertEquals(3, cards.selected(read(owner)).cardIndex());
            assertTrue(guest.channel().outboundMessages().isEmpty());
            world.request(owner, cards.encode(new PqResultSelectCard(1, 2)));

            assertTrue(owner.channel().outboundMessages().isEmpty());
            assertTrue(guest.channel().outboundMessages().isEmpty());
            assertEquals(1, calls.get());
            world.request(guest, RoomScenario.ready(1));
            world.request(owner, RoomFixtures.root("start-empty"));
            world.drainOutputs();
            world.request(owner, cards.encode(new PqResultSelectCard(3, 0)));

            assertEquals(1, calls.get());
            assertTrue(owner.channel().outboundMessages().isEmpty());
            assertTrue(guest.channel().outboundMessages().isEmpty());
            assertTrue(owner.channel().isActive());
            assertTrue(guest.channel().isActive());
        }
    }
}
