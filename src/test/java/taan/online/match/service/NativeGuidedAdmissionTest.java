package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchGuidedTestPackets;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchBonusOffer;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.SpentMatchItem;
import taan.online.match.service.model.UseMatchItemCommand;

class NativeGuidedAdmissionTest {
    @Test
    void admitsBothPhysicalNativeSlotsAndTheActualRetargetWithoutInventingMissingGuidanceEvents() throws Exception {
        for (String name : List.of(
            "00318-s2c-PcGamePlay_Scenario.hex",
            "00353-s2c-PcGamePlay_Scenario.hex",
            "00159-c2s-combat.hex"
        )) {
            try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY)) {
                byte[] root = MatchGuidedTestPackets.root(name);
                var packet = new ScenarioCodec().decode(root);

                if (packet.shooterSlot() == 2) {
                    assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
                }

                grant(p.f, packet);
                assertTrue(p.energy.activate(p.use(0, 7)));
                var before = p.f.runtime.snapshot();
                var member = before.participants().stream().filter(m -> m.slotIndex() == packet.shooterSlot())
                    .findFirst().orElseThrow();

                var journal = new ScenarioRules().validate(before, new ScenarioClaimsMapper().map(packet), member);
                assertEquals(1, journal.missileDefinitions().size());
                assertSame(before, p.f.runtime.snapshot());
                assertArrayEquals(root, new ScenarioCodec().encode(packet));
            }
        }
    }

    @Test
    void commitsTheUnmodifiedNativeRetargetRootWithOneSpendAndBothAcknowledgements() throws Exception {
        try (var boxes = new MatchBoxFixture(MatchGuidedTestPackets.POLICY)) {
            var f = boxes.f;
            byte[] root = MatchGuidedTestPackets.root("00159-c2s-combat.hex");
            var packet = new ScenarioCodec().decode(root);
            grant(f, packet);
            var before = f.runtime.snapshot();
            var context = MatchCommandContext.from(before, f.room.owner);
            var activation = new UseMatchItemCommand(
                context,
                before.turn().revision(),
                before.phaseRevision(),
                0,
                1,
                0,
                0,
                0,
                7
            );
            var use = f.runtime.turnWork(() -> f.runtime.items.activate(activation));
            f.run();
            assertTrue(use.join());
            var spending = new RecordMatchSpendCommand(
                context,
                before.turn().revision(),
                before.phaseRevision(),
                0,
                1,
                255,
                List.of(new SpentMatchItem(7, 0))
            );
            var spend = f.runtime.turnWork(() -> f.runtime.items.record(spending));
            f.run();
            assertTrue(spend.join());
            var command = new ApplyScenarioCommand(
                context,
                before.turn().revision(),
                before.world().revision(),
                new ScenarioClaimsMapper().map(packet),
                root,
                before.phaseRevision()
            );

            assertEquals(ScenarioApplicationOutcome.APPLIED, boxes.apply(command).outcome());
            var committed = f.runtime.snapshot();
            assertEquals(902, committed.actors().get(f.room.players.get(1)).hp());
            assertEquals(1, committed.world().boxes().size());
            assertEquals(1, f.owner.spends.size());
            assertArrayEquals(root, f.guest.scenarios.getFirst().scenario().root());
            assertEquals(ScenarioApplicationOutcome.DUPLICATE, boxes.apply(command).outcome());
            assertSame(committed, f.runtime.snapshot());
            boxes.acknowledge();
            f.timers.now = committed.completion().orElseThrow().settleNotBeforeNanos();
            f.timers.commands.getFirst().run();
            f.run();
            assertEquals(before.turn().revision() + 1, f.runtime.snapshot().turn().revision());
            assertNull(f.callbacks.reason);
        }
    }

    private void grant(MatchRuntimeFixture f, PcGamePlayScenario packet) {
        var member = f.match.participants().stream().filter(m -> m.slotIndex() == packet.shooterSlot())
            .findFirst().orElseThrow();
        var session = member.sessionId();
        var actor = f.match.actors().get(session);
        f.match.move(session, actor.x(), actor.y(), packet.facing(), f.timers.now);
        f.match.updateLoot(f.match.loot().offered(new MatchBonusOffer(
            session,
            member.admissionId(),
            f.match.turn().revision(),
            0,
            false
        )).granted(session, 7).confirmed(session, member.admissionId(), 0, 7));
        f.runtime.refresh();
    }
}
