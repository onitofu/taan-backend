package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.read;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.MatchResultAssertions;
import taan.online.LogCapture;
import taan.online.MatchRepeatTestPackets;
import taan.online.TeamScenarioPackets;
import taan.online.TestFixtures;
import taan.online.match.model.MatchItemSource;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.SpendItemCodec;
import taan.online.match.protocol.codec.UseItemCodec;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioIntPoint;
import taan.online.match.protocol.packet.ScenarioMakeItemBoxCommand;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.room.service.model.RoomMatchToken;

class ChannelLootConnectionTest {
    @Test
    void keepsTwoChannelPoolsIndependentAndRejectsRewardsBeforeTheirNativeThreshold() throws Exception {
        for (int scalar : new int[]{50, 70, 89}) {
            try (var f = new ChannelLootConnectionFixture(0, scalar, 4); var logs = new LogCapture()) {
                var low = f.start(1, false, 0, 0);
                var high = f.start(3, false, 3, scalar);
                var lowMember = f.active(low);
                var highMember = f.active(high);
                f.pass(low);
                f.pass(high);
                var lowBefore = f.snapshot(low);
                f.world.request(f.client(lowMember), f.bonus(lowMember, 3));
                assertEquals(lowBefore, f.snapshot(low));
                f.empty(low);
                f.empty(high);

                int item = scalar >= 70 ? 2 : 3;
                byte[] bonus = f.bonus(highMember, item);
                f.world.request(f.client(highMember), bonus);
                f.expectBoth(high, bonus);
                assertEquals(lowBefore, f.snapshot(low));
                assertEquals(1, f.snapshot(high).loot().grantSequence());
                f.empty(low);
                f.world.request(f.client(highMember), bonus);
                f.confirm(highMember, 0, item);
                f.confirm(highMember, 0, item);
                f.confirm(lowMember, 0, item);
                assertEquals(lowBefore, f.snapshot(low));
                assertEquals(1, f.snapshot(high).loot().grantSequence());
                f.empty(high);
                f.empty(low);
                assertTrue(logs.text().contains("lootPickerScalar=\"" + scalar + "\""));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
            }
        }
    }

    @Test
    void receivesConfirmsAndUsesNativeNewItemsFromBothSourcesInBothModesWithEverySupportedWeapon() throws Exception {
        for (boolean captain : new boolean[]{false, true}) {
            for (int weapon : new int[]{0, 3, 8, 10}) {
                for (int item : new int[]{2, 3, 4}) {
                    for (boolean bonusSource : new boolean[]{false, true}) {
                        int scalar = item == 4 ? 100 : 70;

                        try (var f = new ChannelLootConnectionFixture(scalar, 0, 2); var logs = new LogCapture()) {
                            var token = f.start(1, captain, weapon, scalar);
                            var member = f.active(token);

                            if (bonusSource) {
                                f.pass(token);
                                byte[] bonus = f.bonus(member, item);
                                f.world.request(f.client(member), bonus);
                                f.expectBoth(token, bonus);
                                f.world.request(f.client(member), bonus);
                            } else {
                                byte[] root = new ScenarioCodec().encode(
                                    pickup(f.snapshot(token), item, item == 4 ? 466 : item == 2 ? 61 : 71)
                                );
                                f.world.request(f.client(member), root);
                                f.world.request(f.client(member), root);
                                relay(f, token, member.sessionId(), root);
                            }

                            var inventory = f.snapshot(token).loot().inventory(member.sessionId());
                            assertEquals(List.of(item, 65535, 65535), inventory.slots());
                            assertEquals(1, inventory.receipts().size());
                            assertEquals(
                                bonusSource ? MatchItemSource.END_TURN_BONUS : MatchItemSource.PROJECTILE_PICKUP,
                                inventory.receipts().getFirst().source()
                            );
                            f.confirm(member, 0, item);
                            f.confirm(member, 0, item);
                            assertTrue(f.snapshot(token).loot().inventory(member.sessionId()).receipts()
                                .getFirst().confirmed());

                            if (!bonusSource) {
                                f.ack(token);
                            }

                            f.pass(token);
                            assertEquals(member.sessionId(), f.active(token).sessionId());
                            var beforeUse = f.snapshot(token);
                            int team = (member.slotIndex() & 2) == 0 ? 1 : 2;
                            byte[] use = new UseItemCodec().encode(new PcGamePlayUseItem(
                                member.slotIndex(),
                                team,
                                0,
                                0,
                                0,
                                item
                            ));
                            f.world.request(f.client(member), use);
                            f.expectBoth(token, use);
                            byte[] spend = new SpendItemCodec().encode(new PcGamePlaySpendItem(
                                member.slotIndex(),
                                team,
                                255,
                                List.of()
                            ));
                            f.world.request(f.client(member), spend);
                            var selected = beforeUse.captain(member.slotIndex()) ? List.of(1, item) : List.of(item);
                            var packet = TeamScenarioPackets.shot(
                                beforeUse,
                                selected,
                                member.slotIndex() == 0 ? 2 : 0,
                                0
                            );
                            byte[] root = new ScenarioCodec().encode(packet);
                            f.world.request(f.client(member), root);
                            f.world.request(f.client(member), root);
                            assertEquals(beforeUse.world().revision() + 1, f.snapshot(token).world().revision());
                            f.expectBoth(token, spend);
                            relay(f, token, member.sessionId(), root);
                            assertEquals(List.of(65535, 65535, 65535), f.snapshot(token).loot()
                                .inventory(member.sessionId()).slots());
                            assertEquals(1, f.snapshot(token).loot().grantSequence());
                            f.ack(token);
                            assertTrue(f.snapshot(token).attacks().isEmpty());
                            assertTrue(
                                logs.text().contains(bonusSource ? "match.bonus.granted" : "match.box.picked_up")
                            );
                            assertTrue(logs.text().contains("lootPickerScalar=\"" + scalar + "\""));
                            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                        }
                    }
                }
            }
        }
    }

    @Test
    void keepsFullInventoryAndRepeatedReportsIdempotentForTheNewRewards() throws Exception {
        try (var f = new ChannelLootConnectionFixture(100, 0, 2)) {
            var token = f.start(1, false, 0, 100);
            var member = f.active(token);

            for (int index = 0; index < 4; index++) {
                f.pass(token);
                int item = 2 + index % 3;
                byte[] root = f.bonus(member, item);
                f.world.request(f.client(member), root);
                f.expectBoth(token, root);
                f.world.request(f.client(member), root);

                if (index < 3) {
                    f.confirm(member, index, item);
                }

                f.pass(token);
            }

            assertEquals(List.of(2, 3, 4), f.snapshot(token).loot().inventory(member.sessionId()).slots());
            assertEquals(3, f.snapshot(token).loot().grantSequence());
            assertEquals(3, f.snapshot(token).loot().inventory(member.sessionId()).receipts().size());
            f.empty(token);
        }
    }

    @Test
    void cancelsUnpermittedOrFalsifiedPickupRewardsWithoutDisconnectingPlayers() throws Exception {
        for (int[] sample : new int[][]{{0, 2, 61}, {70, 4, 61}, {70, 2, 0}}) {
            try (var f = new ChannelLootConnectionFixture(sample[0], 0, 2); var logs = new LogCapture()) {
                var token = f.start(1, false, 0, sample[0]);
                var member = f.active(token);
                var invalid = pickup(f.snapshot(token), sample[1], sample[2]);
                f.world.request(f.client(member), new ScenarioCodec().encode(invalid));
                assertTrue(f.world.matches.current(token).isEmpty());

                for (var client : f.clients) {
                    MatchResultAssertions.readEnd(() -> read(client), 0);
                    assertTrue(client.channel().isActive());
                }

                assertTrue(logs.text().contains(sample[1] == 2 && sample[0] == 70
                    ? "INVALID_PICKUP_REWARD" : "PICKUP_NOT_PERMITTED"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
            }
        }
    }

    private PcGamePlayScenario pickup(MatchSnapshot snapshot, int item, int draw) throws Exception {
        int slot = snapshot.turn().activeSlot();
        var base = TeamScenarioPackets.shot(
            snapshot,
            snapshot.captain(slot) ? List.of(1) : List.of(),
            slot == 0 ? 2 : 0,
            0
        );
        var commands = new ArrayList<>(base.scenario().commands());
        long box = 0x0500000000000001L;
        commands.add(new ScenarioMakeItemBoxCommand(
            new ScenarioCommandHeader(slot, 10, 710, 0),
            box,
            new ScenarioIntPoint(200, 200)
        ));
        commands.add(new ScenarioEatItemBoxCommand(new ScenarioCommandHeader(slot, 11, 711, 0), box, item, draw));
        return MatchRepeatTestPackets.commands(base, base.scenario().selectedItems(), 0, commands);
    }

    private void relay(ChannelLootConnectionFixture f, RoomMatchToken token, UUID sender, byte[] expected) {
        for (var member : f.snapshot(token).participants()) {
            if (!member.sessionId().equals(sender)) {
                assertArrayEquals(expected, read(f.client(member)));
            }
        }

        f.empty(token);
    }
}
