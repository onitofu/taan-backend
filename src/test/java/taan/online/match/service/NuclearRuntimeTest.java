package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchRepeatTestPackets;
import taan.online.TeamScenarioPackets;
import taan.online.TestFixtures;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.WeaponMissileKey;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.ScenarioApplicationOutcome;

class NuclearRuntimeTest {
    @Test
    void consumesDisplacedCardsAndAppliesNuclearPowerAndBaseSpecialOnceInEitherOrder() throws Exception {
        for (int modifier : new int[]{1, 2, 3, 4, 5, 6, 7, 1020}) {
            for (boolean first : new boolean[]{false, true}) {
                try (var p = new MatchPowerFixture(MatchLootPolicy.nativeItems())) {
                    p.energy.charge();
                    var before = p.f.runtime.snapshot();

                    for (int item : first ? new int[]{modifier, 4} : new int[]{4, modifier}) {
                        if (item == 6) {
                            assertTrue(p.energy.activate(p.energy.use()));
                        } else {
                            p.grant(0, item, true);
                            assertTrue(p.energy.activate(p.use(0, item)));
                        }
                    }

                    var snapshot = p.f.runtime.snapshot();
                    var session = p.f.room.owner;
                    var selected = switch (modifier) {
                        case 4 -> List.of(4);
                        case 5 -> List.of(5, 4);
                        case 6 -> List.of(6);
                        default -> List.of(4, modifier);
                    };
                    assertEquals(selected, snapshot.attacks().get(session).selectedItems());
                    assertEquals(modifier == 6 ? 0 : 100, snapshot.actors().get(session).energy());
                    assertEquals(before.actors().get(session).hp(), snapshot.actors().get(session).hp());
                    assertEquals(List.of(65535, 65535, 65535), snapshot.loot().inventory(session).slots());
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                    var packet = TeamScenarioPackets.shot(snapshot, selected, 2, 77);
                    int damage = packet.scenario().commands().stream().filter(ScenarioDamageCommand.class::isInstance)
                        .map(ScenarioDamageCommand.class::cast).mapToInt(ScenarioDamageCommand::damage).sum();
                    var shot = command(p, packet);
                    var result = p.apply(shot);
                    assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome());
                    assertEquals(
                        new WeaponMissileKey(modifier == 6 ? 0 : 100, modifier == 6 ? 2 : modifier == 5 ? 1 : 0),
                        result.committed().orElseThrow().scenario().journal().missileDefinitions().get(0)
                    );
                    var committed = p.f.runtime.snapshot();
                    assertEquals(snapshot.world().revision() + 1, committed.world().revision());
                    assertEquals(
                        snapshot.actors().get(p.f.room.players.get(1)).hp() - damage,
                        committed.actors().get(p.f.room.players.get(1)).hp()
                    );
                    assertEquals(ScenarioApplicationOutcome.DUPLICATE, p.apply(shot).outcome());
                    assertEquals(committed, p.f.runtime.snapshot());
                    assertTrue(committed.attacks().isEmpty());
                    p.energy.acknowledge();
                    assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
                    assertEquals(
                        ScenarioApplicationOutcome.APPLIED,
                        p.apply(command(p, TeamScenarioPackets.shot(p.f.runtime.snapshot(), List.of(), 2, 0))).outcome()
                    );
                }
            }
        }
    }

    @Test
    void releasesActiveNuclearSelectionAndItsReceiptsWhenTheRuntimeStops() throws Exception {
        try (var p = new MatchPowerFixture(MatchLootPolicy.nativeItems())) {
            p.grant(0, 4, true);
            assertTrue(p.energy.activate(p.use(0, 4)));
            assertFalse(p.f.runtime.snapshot().attacks().isEmpty());
            p.f.runtime.stop();
            p.f.runtime.finish(() -> { });
            p.f.run();

            assertTrue(p.f.match.attacks().isEmpty());
            assertTrue(p.f.match.loot().inventories().isEmpty());
            assertTrue(p.f.match.loot().offers().isEmpty());
        }
    }

    @Test
    void rejectsAnUnconfirmedCardAndForgedNuclearFireWithoutSuccessfulEchoOrSecretLeakage() throws Exception {
        try (var p = new MatchPowerFixture(MatchLootPolicy.nativeItems()); var logs = new LogCapture()) {
            p.grant(0, 4, false);
            assertFalse(p.energy.activate(p.use(0, 4)));
            assertTrue(p.f.owner.uses.isEmpty());
            assertTrue(logs.text().contains("match.nuclear.rejected"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
        }

        try (var p = new MatchPowerFixture(MatchLootPolicy.nativeItems())) {
            var before = p.f.runtime.snapshot();
            var nuclear = TeamScenarioPackets.shot(before, List.of(4), 2, 77);
            var forged = MatchRepeatTestPackets.commands(nuclear, List.of(), 0, nuclear.scenario().commands());
            assertTrue(p.energy.record(p.energy.spend(List.of())));
            assertEquals(ScenarioApplicationOutcome.REJECTED, p.apply(command(p, forged)).outcome());
            assertEquals(before.actors(), p.f.runtime.snapshot().actors());
            assertTrue(p.f.owner.scenarios.isEmpty());
            assertTrue(p.f.guest.scenarios.isEmpty());
        }
    }

    private ApplyScenarioCommand command(MatchPowerFixture p, PcGamePlayScenario packet) {
        var snapshot = p.f.runtime.snapshot();
        return new ApplyScenarioCommand(
            p.energy.context(),
            snapshot.turn().revision(),
            snapshot.world().revision(),
            new ScenarioClaimsMapper().map(packet),
            new ScenarioCodec().encode(packet),
            snapshot.phaseRevision()
        );
    }
}
