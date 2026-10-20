package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchRepeatTestPackets;
import taan.online.TestFixtures;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.ScenarioBaseCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.SpentMatchItem;

class MatchRepeatTest {
    @Test
    void appliesAllAuthorizedLaunchesInOneCommitAndOneCompletionWithoutRepeatingDamageOrEnergy() throws Exception {
        for (var items : List.of(List.of(1), List.of(2), List.of(1, 1), List.of(2, 1, 1), List.of(2, 2, 2))) {
            try (var p = new MatchRepeatFixture(); var logs = new LogCapture()) {
                for (int item : items) {
                    p.grant(item, true);
                    var command = p.use(0, item);
                    assertTrue(p.energy.activate(command));
                    assertTrue(p.energy.activate(command));
                }

                int count = 1 + items.stream().mapToInt(Integer::intValue).sum();
                var before = p.f.runtime.snapshot();
                var selected = before.attacks().get(p.f.room.owner);
                assertEquals(count, selected.shotLimit());
                assertEquals(0, selected.attackKind());
                assertEquals(items.size(), p.f.owner.uses.size());
                assertEquals(0, before.actors().get(p.f.room.owner).energy());
                var spends = items.stream().map(item -> new SpentMatchItem(item, 0)).toList();
                assertTrue(p.energy.record(p.energy.spend(spends)));
                assertTrue(p.energy.record(p.energy.spend(spends)));
                var scene = p.shot(count);

                var result = p.apply(scene);
                assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome());
                var committed = result.committed().orElseThrow();
                assertEquals(count, committed.scenario().journal().missileDefinitions().size());
                assertTrue(committed.changedCells() > 0);
                var after = p.f.runtime.snapshot();
                assertEquals(before.world().revision() + 1, after.world().revision());
                assertEquals(
                    before.actors().get(p.f.room.players.get(1)).hp() - count * 10,
                    after.actors().get(p.f.room.players.get(1)).hp()
                );
                assertEquals(2, after.actors().get(p.f.room.owner).energy());
                assertEquals(4, after.actors().get(p.f.room.players.get(1)).energy());
                assertTrue(after.attacks().isEmpty());
                assertTrue(after.loot().inventory(p.f.room.owner).receipts().isEmpty());
                assertEquals(1, p.f.owner.spends.size());
                assertEquals(1, p.f.guest.spends.size());
                assertTrue(p.f.owner.scenarios.isEmpty());
                assertArrayEquals(new ScenarioCodec().encode(scene), p.f.guest.scenarios.getFirst().scenario().root());
                assertEquals(ScenarioApplicationOutcome.DUPLICATE, p.apply(scene).outcome());
                assertEquals(after, p.f.runtime.snapshot());

                var ownerAck = p.energy.movement.live.ack(0, false);
                var guestAck = p.energy.movement.live.ack(1, false);
                p.energy.acknowledge();
                var next = p.f.runtime.snapshot();
                assertEquals(before.turn().revision() + 1, next.turn().revision());
                p.f.runtime.confirmScenario(ownerAck);
                p.f.runtime.confirmScenario(guestAck);
                p.f.run();
                assertEquals(next, p.f.runtime.snapshot());
                assertNull(p.f.callbacks.reason);
                assertTrue(logs.text().contains("match.repeat.activated"));
                assertTrue(logs.text().contains("match.repeat.duplicate"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            }
        }
    }

    @Test
    void admitsTheUnmodifiedNativePlusOneScenarioAgainstTheAuthorizedSelection() throws Exception {
        try (var p = new MatchRepeatFixture()) {
            p.grant(1, true);
            assertTrue(p.energy.activate(p.use(0, 1)));
            byte[] root = MatchRepeatTestPackets.root("00176-s2c-PcGamePlay_Scenario.hex");
            var packet = new ScenarioCodec().decode(root);
            var snapshot = p.f.runtime.snapshot();
            var member = snapshot.participants().getFirst();

            var journal = new ScenarioRules().validate(snapshot, new ScenarioClaimsMapper().map(packet), member);
            assertEquals(2, journal.missileDefinitions().size());
            assertEquals(0, journal.missileDefinitions().get(0));
            assertEquals(0, journal.missileDefinitions().get(1));
            assertEquals(snapshot, p.f.runtime.snapshot());
        }
    }

    @Test
    void combinesBothRepeatCardsWithPowerAndSpecialInEitherActivationOrder() throws Exception {
        for (int item : new int[]{1, 2}) {
            for (int attack : new int[]{5, 6}) {
                for (boolean repeatFirst : new boolean[]{false, true}) {
                    try (var p = new MatchRepeatFixture()) {
                        if (attack == 6) {
                            p.energy.charge();
                        }

                        int[] order = repeatFirst ? new int[]{item, attack} : new int[]{attack, item};

                        for (int selected : order) {
                            if (selected == 6) {
                                assertTrue(p.energy.activate(p.energy.use()));
                            } else {
                                p.grant(selected, true);
                                assertTrue(p.energy.activate(p.use(0, selected)));
                            }
                        }

                        var selection = p.f.runtime.snapshot().attacks().get(p.f.room.owner);
                        assertEquals(List.of(attack, item), selection.selectedItems());
                        assertEquals(attack == 6 ? 2 : 1, selection.attackKind());
                        assertTrue(p.energy.record(p.energy.spend(List.of())));
                        var result = p.apply(p.shot(1 + item));
                        assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome());
                        int missile = attack == 6 ? 2 : 1;
                        assertTrue(result.committed().orElseThrow().scenario().journal().missileDefinitions()
                            .values().stream().allMatch(value -> value == missile));
                        p.energy.acknowledge();
                        assertNull(p.f.callbacks.reason);

                        if (attack == 6) {
                            p.energy.charge();
                            assertTrue(p.energy.activate(p.energy.use()));
                        }
                    }
                }
            }
        }
    }

    @Test
    void preservesAccumulatedCandidatesAcrossPassesAndSelectsOnlyThreeRepeatCards() throws Exception {
        try (var p = new MatchRepeatFixture()) {
            for (int item : new int[]{1, 1, 1, 2}) {
                p.grant(item, true);
                assertTrue(p.energy.activate(p.use(0, item)));
                assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
                assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
            }

            var selection = p.f.runtime.snapshot().attacks().get(p.f.room.owner);
            assertEquals(List.of(1, 1, 1, 2), selection.activeItems());
            assertEquals(List.of(2, 1, 1), selection.selectedItems());
            assertEquals(5, selection.shotLimit());
            assertTrue(p.energy.record(p.energy.spend(List.of())));
            assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(p.shot(5)).outcome());
        }
    }

    @Test
    void acceptsAnEarlyDeathBeforeTheRemainingAuthorizedLaunches() throws Exception {
        try (var p = new MatchRepeatFixture()) {
            p.grant(2, true);
            assertTrue(p.energy.activate(p.use(0, 2)));
            assertTrue(p.energy.record(p.energy.spend(List.of())));
            var base = p.shot(1);
            var commands = new ArrayList<>(base.scenario().commands());
            commands.add(new ScenarioBaseCommand(new ScenarioCommandHeader(0, 5, 1300, 0)));
            var scene = MatchRepeatTestPackets.commands(base, List.of(2), 0, commands);

            assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(scene).outcome());
            assertTrue(p.f.runtime.snapshot().actors().get(p.f.room.owner).dead());
            assertTrue(p.f.runtime.snapshot().attacks().isEmpty());
        }
    }
}
