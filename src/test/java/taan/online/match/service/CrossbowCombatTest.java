package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import taan.online.CrossbowFixtures;
import taan.online.LogCapture;
import taan.online.MatchRepeatTestPackets;
import taan.online.TeamScenarioPackets;
import taan.online.TestFixtures;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.WeaponMissileKey;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioGuidedCommand;
import taan.online.match.protocol.packet.ScenarioIntPoint;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.match.protocol.packet.ScenarioWeaponFields;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.MoveMatchActorCommand;
import taan.online.match.service.model.ScenarioAdmissionContext;
import taan.online.match.service.model.ScenarioAdmissionOutcome;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.model.RoomMode;

class CrossbowCombatTest {
    @Test
    void keepsTheVictimWatchingAfterACrossbowKillInBothTeamModes() throws Exception {
        for (var mode : List.of(RoomMode.ITEM, RoomMode.CAPTAIN_ITEM)) {
            try (var f = new TeamMatchFixture(2, 2, mode, 3)) {
                f.start(true);
                var shooter = f.active();
                var victim = f.snapshot().participants().stream().filter(p -> p.team() != shooter.team())
                    .filter(p -> !f.snapshot().captain(p.slotIndex())).findFirst().orElseThrow();
                var result = f.shot(victim.slotIndex(), 10000);
                assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome(), result.reason());
                assertEquals(1, f.snapshot().actors().values().stream().filter(actor -> actor.dead()).count());
                assertEquals(0, f.snapshot().actors().get(victim.sessionId()).hp());
                assertTrue(f.snapshot().resolution().pendingOutcome().isEmpty());
                assertEquals(4, f.snapshot().completion().orElseThrow().awaitingSessions().size());

                complete(f);
                assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                assertTrue(f.outputs.get(victim.sessionId()).closed.isEmpty());

                for (int index = 0; index < 6; index++) {
                    assertFalse(f.active().sessionId().equals(victim.sessionId()));
                    f.pass();
                }
            }
        }
    }

    @Test
    void appliesNativeShotsOnceAndKeepsIntrinsicGuidanceSeparateFromThePersonalInventory() throws Exception {
        for (var name : List.of("a", "b", "special")) {
            try (var f = new TeamMatchFixture(1, 1, RoomMode.ITEM, 3); var logs = new LogCapture()) {
                f.start(true);
                var packet = new ScenarioCodec().decode(CrossbowFixtures.root(name));

                if (name.equals("special")) {
                    charge(f);
                }

                while (f.active().slotIndex() != packet.shooterSlot()) {
                    f.pass();
                }

                if (name.equals("special")) {
                    f.activate(6);
                    f.spend();
                }

                var before = f.snapshot();
                var result = f.apply(packet);
                assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome(), result.reason());
                assertEquals(before.world().revision() + 1, f.snapshot().world().revision());
                var committed = f.snapshot().world();
                assertEquals(ScenarioApplicationOutcome.DUPLICATE, f.apply(packet).outcome());
                assertEquals(committed, f.snapshot().world());
                assertTrue(f.snapshot().attacks().isEmpty());
                assertTrue(result.committed().orElseThrow().scenario().journal().missileDefinitions().values()
                    .stream().allMatch(key -> key.equals(new WeaponMissileKey(3, name.equals("special") ? 2 : 0))));

                if (name.equals("special")) {
                    var victim = before.participants().stream().filter(
                        p -> p.slotIndex() == 2
                    ).findFirst().orElseThrow();
                    assertEquals(815, committed.actors().get(victim.sessionId()).hp());
                    assertEquals(18, committed.actors().get(f.active().sessionId()).energy());
                    assertTrue(result.committed().orElseThrow().changedCells() > 0);
                    assertFalse(before.attacks().get(f.active().sessionId()).activeItems().contains(7));
                }

                complete(f);
                assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                assertTrue(f.outputs.values().stream().allMatch(output -> output.closed.isEmpty()));
                assertTrue(logs.text().contains("weaponId=\"3\""));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
            }
        }
    }

    @Test
    void acceptsAnotherShotAfterNativeTerrainDestructionAndMovement() throws Exception {
        try (var f = new TeamMatchFixture(1, 1, RoomMode.ITEM, 3)) {
            f.start(true);

            while (f.active().slotIndex() != 2) {
                f.pass();
            }

            var first = f.apply(new ScenarioCodec().decode(CrossbowFixtures.root("b")));
            assertEquals(ScenarioApplicationOutcome.APPLIED, first.outcome(), first.reason());
            assertTrue(first.committed().orElseThrow().changedCells() > 0);
            complete(f);
            var before = f.snapshot();
            var actor = before.actors().get(f.active().sessionId());
            assertTrue(f.scenario.matches.move(new MoveMatchActorCommand(
                f.context(f.active().sessionId()),
                before.turn().revision(),
                before.phaseRevision(),
                f.active().slotIndex(),
                actor.x() + 1,
                actor.y(),
                actor.facing(),
                MatchMovementKind.MOVE
            )).toCompletableFuture().join());

            long revision = f.snapshot().world().revision();
            var second = f.shot(2, 0);
            assertEquals(ScenarioApplicationOutcome.APPLIED, second.outcome(), second.reason());
            complete(f);
            assertEquals(MatchPhase.ACTION, f.snapshot().phase());
            assertEquals(revision + 1, second.committed().orElseThrow().snapshot().world().revision());
        }
    }

    @Test
    void rejectsGuidanceWithoutPermissionAndRejectsInvalidSpecialReferencesWithoutMutatingTheMatch() throws Exception {
        try (var f = new TeamMatchFixture(1, 1, RoomMode.ITEM, 3); var logs = new LogCapture()) {
            f.start(true);

            for (int kind : new int[]{0, 1, 2}) {
                if (kind == 1) {
                    f.activate(5);
                } else if (kind == 2) {
                    charge(f);
                    f.activate(6);
                }

                var snapshot = f.snapshot();
                var packet = TeamScenarioPackets.shot(snapshot, f.selected(), 2, 0);
                var validator = new ValidateScenarioAction((context, member) -> true);
                var context = new ScenarioAdmissionContext(
                    snapshot,
                    f.context(f.active().sessionId()),
                    snapshot.turn().revision(),
                    Optional.empty()
                );

                for (int mutation = 0; mutation < (kind == 2 ? 5 : 1); mutation++) {
                    var invalid = invalid(packet, mutation, kind);
                    var result = validator.execute(
                        context,
                        new ScenarioClaimsMapper().map(invalid),
                        new ScenarioCodec().encode(invalid)
                    );
                    assertEquals(ScenarioAdmissionOutcome.REJECTED, result.outcome());
                    assertEquals(
                        kind != 2 ? "UNAUTHORIZED_GUIDANCE" : new String[]{
                            "INVALID_FIRE",
                            "INVALID_PROJECTILE_REFERENCE",
                            "MISSING_PROJECTILE",
                            "INVALID_ATTACK_ANIMATION",
                            "INVALID_FIRE"
                        }[mutation],
                        result.reason()
                    );
                    assertEquals(snapshot, f.snapshot());
                }
            }

            assertTrue(logs.text().contains("match.scenario.rejected"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
        }
    }

    private void complete(TeamMatchFixture f) {
        f.acknowledge();

        if (f.snapshot().phase() == MatchPhase.COMPLETION) {
            var timers = f.scenario.matchTimers;
            timers.now = f.snapshot().completion().orElseThrow().settleNotBeforeNanos();
            timers.advanceSeconds(0);
        }
    }

    private void charge(TeamMatchFixture f) {
        for (int index = 0; index < 100; index++) {
            f.pass();
        }

        assertEquals(100, f.snapshot().actors().get(f.active().sessionId()).energy());
    }

    private PcGamePlayScenario invalid(PcGamePlayScenario packet, int mutation, int kind) {
        var commands = new ArrayList<>(packet.scenario().commands());
        var fire = commands.stream().filter(ScenarioFireCommand.class::isInstance)
            .map(ScenarioFireCommand.class::cast).findFirst().orElseThrow();

        if (kind != 2) {
            commands.add(new ScenarioGuidedCommand(
                new ScenarioCommandHeader(packet.shooterSlot(), 13, fire.header().tick() + 200, 0),
                fire.weapon(),
                new ScenarioIntPoint(1000, 650)
            ));
        } else if (mutation == 0 || mutation == 4) {
            var w = fire.weapon();
            commands.set(commands.indexOf(fire), new ScenarioFireCommand(
                fire.header(),
                new ScenarioWeaponFields(
                    w.sequence(),
                    mutation == 0 ? 2 : w.variant(),
                    w.position(),
                    w.shotTick(),
                    w.scalar24Bits(),
                    w.scalar28()
                ),
                mutation == 4 ? 0 : fire.weaponId(),
                fire.attackKind(),
                fire.velocity(),
                fire.acceleration(),
                fire.byte40(),
                fire.word42()
            ));
        } else if (mutation == 3) {
            var old = (ScenarioNormalCommand) commands.getFirst();
            commands.set(0, new ScenarioNormalCommand(old.header(), 39, old.scalar18()));
        } else {
            var guided = commands.stream().filter(ScenarioGuidedCommand.class::isInstance)
                .map(ScenarioGuidedCommand.class::cast).findFirst().orElseThrow();
            var w = guided.weapon();
            commands.set(commands.indexOf(guided), new ScenarioGuidedCommand(
                mutation == 2 ? new ScenarioCommandHeader(packet.shooterSlot(), 13, fire.header().tick() - 1, 0)
                    : guided.header(),
                new ScenarioWeaponFields(
                    w.sequence(),
                    mutation == 1 ? 2 : w.variant(),
                    w.position(),
                    w.shotTick(),
                    0,
                    0
                ),
                guided.target()
            ));
        }

        return MatchRepeatTestPackets.commands(packet, packet.scenario().selectedItems(), kind, commands);
    }
}
