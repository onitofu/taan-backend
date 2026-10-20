package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchRepeatTestPackets;
import taan.online.MatchSpreadTestPackets;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchBonusOffer;
import taan.online.match.protocol.codec.ScenarioCodec;

class NativeSpreadAdmissionTest {
    @Test
    void admitsBothUnmodifiedNativeSpreadRootsWithTheirVerifiedActorsAndActiveSelection() throws Exception {
        for (String name : List.of("00246-s2c-PcGamePlay_Scenario.hex", "00283-s2c-PcGamePlay_Scenario.hex")) {
            try (var p = new MatchRepeatFixture(MatchSpreadTestPackets.POLICY)) {
                byte[] root = MatchSpreadTestPackets.root(name);
                var packet = new ScenarioCodec().decode(root);

                if (packet.shooterSlot() == 2) {
                    assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
                }

                var member = p.f.match.participants().stream()
                    .filter(value -> value.slotIndex() == packet.shooterSlot()).findFirst().orElseThrow();
                var session = member.sessionId();
                var actor = p.f.match.actors().get(session);
                p.f.match.move(session, actor.x(), actor.y(), packet.facing(), p.f.timers.now);
                var loot = p.f.match.loot().offered(new MatchBonusOffer(
                    session,
                    member.admissionId(),
                    p.f.match.turn().revision(),
                    0,
                    false
                )).granted(session, 3).confirmed(session, member.admissionId(), 0, 3);
                p.f.match.updateLoot(loot);
                assertTrue(p.f.match.useItem(session, 0, 3));
                p.f.runtime.refresh();
                var before = p.f.runtime.snapshot();

                var journal = new ScenarioRules().validate(before, new ScenarioClaimsMapper().map(packet), member);
                assertEquals(3, journal.missileDefinitions().size());
                assertEquals(List.of(0, 0, 0), journal.missileDefinitions().values().stream().sorted().toList());
                assertEquals(before, p.f.runtime.snapshot());
                assertArrayEquals(root, new ScenarioCodec().encode(packet));
            }
        }
    }

    @Test
    void admitsTheHistoricalRepeatSpreadDamageCombinationWithoutRenumberingItsNineProjectiles() throws Exception {
        try (var p = new MatchRepeatFixture(MatchSpreadTestPackets.POLICY)) {
            for (int item : new int[]{2, 3, 1050}) {
                p.grant(item, true);
                assertTrue(p.energy.activate(p.use(0, item)));
            }

            byte[] root = MatchRepeatTestPackets.root("00186-s2c-PcGamePlay_Scenario.hex");
            var packet = new ScenarioCodec().decode(root);
            var snapshot = p.f.runtime.snapshot();
            var member = snapshot.participants().getFirst();
            var journal = new ScenarioRules().validate(snapshot, new ScenarioClaimsMapper().map(packet), member);
            assertEquals(9, journal.missileDefinitions().size());
            assertArrayEquals(root, new ScenarioCodec().encode(packet));
            assertEquals(snapshot, p.f.runtime.snapshot());
        }
    }
}
