package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchActorState;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.service.model.AcceptedScenario;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.ScenarioActorClaim;
import taan.online.match.service.model.ScenarioAdmissionOutcome;
import taan.online.match.service.model.ScenarioClaim;
import taan.online.match.service.model.ScenarioClaims;
import taan.online.match.service.model.ScenarioDamageClaim;
import taan.online.match.service.model.ScenarioEventTime;
import taan.online.match.service.model.ScenarioFallClaim;
import taan.online.match.service.model.ScenarioFireClaim;
import taan.online.match.service.model.ScenarioProjectileClaim;
import taan.online.match.service.model.ScenarioUnsupportedClaim;
import taan.online.room.model.RoomId;

class ValidateScenarioActionTest {
    @Test
    void acceptsIndependentNativeDamageFallAndMarkersWithoutChangingWorld() throws Exception {
        try (var world = new ScenarioTestWorld(); var logs = new LogCapture()) {
            var before = world.snapshot();
            var result = world.execute(world.claims);
            var accepted = result.accepted().orElseThrow();

            assertEquals(ScenarioAdmissionOutcome.ACCEPTED, result.outcome());
            assertEquals(before, world.snapshot());
            assertEquals(4193, accepted.journal().endTick());
            assertEquals(0, accepted.journal().missileDefinitions().get(0));
            assertEquals(world.claims.commands(), accepted.journal().events());
            assertEquals(world.claims.refittedSlots(), accepted.journal().refittedSlots());
            assertEquals(world.sender, accepted.sender());
            assertEquals(7, accepted.turnRevision());
            assertArrayEquals(world.root, accepted.root());
            assertNotSame(world.root, accepted.root());
            byte[] copy = accepted.root();
            copy[0]++;

            assertArrayEquals(world.root, accepted.root());
            byte[] supplied = world.root.clone();
            var defensive = new AcceptedScenario(world.sender, 7, supplied, accepted.journal());
            supplied[0]++;

            assertArrayEquals(world.root, defensive.root());
            assertEquals(AcceptedScenario.hash(world.root), accepted.hash());
            assertEquals("AcceptedScenario[REDACTED]", accepted.toString());
            assertTrue(logs.text().contains("match.scenario.validated"));
            assertTrue(logs.text().contains("scenarioHash="));
            assertTrue(logs.text().contains("commandCount="));
            assertTrue(logs.text().contains("admissionId="));
            assertFalse(logs.text().contains("simulationHp"));
            assertFalse(logs.text().contains("velocityX"));
            assertThrows(UnsupportedOperationException.class, () -> accepted.journal().events().clear());
            assertThrows(UnsupportedOperationException.class, () -> accepted.journal().missileDefinitions().clear());
            assertThrows(UnsupportedOperationException.class, () -> accepted.journal().refittedSlots().clear());
        }
    }

    @Test
    void ignoresStaleIdentityChannelRoomGenerationAndTurnBeforeRules() throws Exception {
        try (var world = new ScenarioTestWorld(); var logs = new LogCapture()) {
            var c = world.sender;
            var contexts = List.of(
                new MatchCommandContext(
                    new MatchId(UUID.randomUUID()),
                    c.roomId(),
                    c.roomGeneration(),
                    c.channelId(),
                    c.sessionId(),
                    c.admissionId()
                ),
                new MatchCommandContext(
                    c.matchId(),
                    new RoomId(2),
                    c.roomGeneration(),
                    c.channelId(),
                    c.sessionId(),
                    c.admissionId()
                ),
                new MatchCommandContext(
                    c.matchId(),
                    c.roomId(),
                    UUID.randomUUID(),
                    c.channelId(),
                    c.sessionId(),
                    c.admissionId()
                ),
                new MatchCommandContext(c.matchId(), c.roomId(), c.roomGeneration(), 2, c.sessionId(), c.admissionId()),
                world.context(UUID.randomUUID(), c.admissionId()),
                world.context(c.sessionId(), UUID.randomUUID())
            );

            for (var sender : contexts) {
                assertEquals(
                    ScenarioAdmissionOutcome.IGNORED,
                    world.action.execute(world.admission(sender, Optional.empty()), world.claims, world.root).outcome()
                );
            }

            world.revision++;
            assertEquals(ScenarioAdmissionOutcome.IGNORED, world.execute(world.claims).outcome());
            world.revision--;
            world.current = false;
            assertEquals(ScenarioAdmissionOutcome.IGNORED, world.execute(world.claims).outcome());
            world.current = true;
            world.phase = MatchPhase.PREVIEW;
            assertEquals(ScenarioAdmissionOutcome.IGNORED, world.execute(world.claims).outcome());
            assertTrue(logs.text().contains("match.scenario.ignored"));
        }
    }

    @Test
    void recognizesOnlyAnExactDuplicateForTheSameCompletionAndTurn() throws Exception {
        try (var world = new ScenarioTestWorld(); var logs = new LogCapture()) {
            var accepted = world.execute(world.claims).accepted().orElseThrow();
            world.phase = MatchPhase.COMPLETION;
            var context = world.admission(world.sender, Optional.of(accepted));

            assertEquals(
                ScenarioAdmissionOutcome.DUPLICATE,
                world.action.execute(context, world.claims, world.root).outcome()
            );
            byte[] changed = world.root.clone();
            changed[changed.length - 1]++;
            assertEquals(
                ScenarioAdmissionOutcome.REJECTED,
                world.action.execute(context, world.claims, changed).outcome()
            );

            for (var previous : List.of(
                new AcceptedScenario(
                    world.context(UUID.randomUUID(), world.sender.admissionId()),
                    7,
                    world.root,
                    accepted.journal()
                ),
                new AcceptedScenario(world.sender, 6, world.root, accepted.journal())
            )) {
                assertEquals(
                    ScenarioAdmissionOutcome.REJECTED,
                    world.action.execute(
                        world.admission(world.sender, Optional.of(previous)),
                        world.claims,
                        world.root
                    ).outcome()
                );
            }

            world.phase = MatchPhase.ACTION;
            assertEquals(
                ScenarioAdmissionOutcome.REJECTED,
                world.action.execute(
                    world.admission(world.sender, Optional.of(accepted)),
                    world.claims,
                    world.root
                ).outcome()
            );
            assertTrue(logs.text().contains("match.scenario.duplicate"));
            assertTrue(logs.text().contains("match.scenario.rejected"));
        }
    }

    @Test
    void rejectsDeadInactiveForgedShootersItemsEffectsPowerAndDuplicateRefittedActors() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            var actor = world.actors.get(world.sender.sessionId());
            world.actors.remove(world.sender.sessionId());
            rejected(world, world.claims, "ACTOR_NOT_ALIVE");
            world.actors.put(world.sender.sessionId(), new MatchActorState(1, 1, 0, 0, 0));
            rejected(world, world.claims, "ACTOR_NOT_ALIVE");
            world.actors.put(world.sender.sessionId(), actor);

            for (var claims : List.of(
                claims(world, 0, 2, 0, 0, .5f, List.of(), false, List.of()),
                claims(world, 2, 0, 0, 0, .5f, List.of(), false, List.of()),
                claims(world, 2, 2, 1, 0, .5f, List.of(), false, List.of())
            )) {
                rejected(world, claims, "INVALID_SHOOTER");
            }

            world.activeSlot = 0;
            rejected(world, world.claims, "INVALID_SHOOTER");
            world.activeSlot = 2;

            for (var claims : List.of(
                claims(world, 2, 2, 0, 0, .5f, List.of(1), false, List.of()),
                claims(world, 2, 2, 0, 0, .5f, List.of(), true, List.of()),
                claims(world, 2, 2, 0, 1, .5f, List.of(), false, List.of())
            )) {
                rejected(world, claims, "UNSUPPORTED_ATTACK_FEATURE");
            }

            world.boxBudget = 1;
            rejected(world, world.claims, "UNSUPPORTED_ATTACK_FEATURE");
            world.boxBudget = 0;

            for (float power : new float[]{Float.NaN, -.01f, 1.01f}) {
                rejected(world, claims(world, 2, 2, 0, 0, power, List.of(), false, List.of()), "INVALID_POWER");
            }

            for (var slots : List.of(List.of(0, 0), List.of(7))) {
                rejected(world, claims(world, 2, 2, 0, 0, .5f, List.of(), false, slots), "INVALID_REFITTED_SLOTS");
            }

            rejected(world, world.commands(List.of()), "INVALID_COMMAND_COUNT");
            rejected(
                world,
                world.commands(Collections.nCopies(4097, world.claims.commands().getFirst())),
                "INVALID_COMMAND_COUNT"
            );
            assertEquals(
                ScenarioAdmissionOutcome.REJECTED,
                world.action.execute(world.admission(), world.claims, new byte[65537]).outcome()
            );
        }
    }

    @Test
    void rejectsInvalidAndDuplicateFireAndNonfiniteKinematics() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            var fire = (ScenarioFireClaim) world.claims.commands().get(1);
            var data = new int[][]{{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}};

            for (var v : data) {
                var bad = new ScenarioFireClaim(fire.header(), v[0], v[1], fire.shotTick(), v[2], v[3], 1, 2, 3, 4);
                rejected(world, replace(world, 1, bad), "INVALID_FIRE");
            }

            for (int component = 0; component < 4; component++) {
                float[] v = {1, 2, 3, 4};
                v[component] = Float.POSITIVE_INFINITY;
                rejected(
                    world,
                    replace(world, 1, new ScenarioFireClaim(fire.header(), 0, 0, 700, 0, 0, v[0], v[1], v[2], v[3])),
                    "NONFINITE_FIRE"
                );
            }

            var duplicate = new ArrayList<>(world.claims.commands());
            duplicate.add(2, fire);
            rejected(world, world.commands(duplicate), "DUPLICATE_FIRE");
            rejected(world, replace(world, 1, new ScenarioActorClaim(fire.header())), "MISSING_PROJECTILE");
            rejected(
                world,
                world.commands(List.of(new ScenarioActorClaim(new ScenarioEventTime(2, 8, 1000, 0)))),
                "MISSING_FIRE"
            );
        }
    }

    @Test
    void rejectsInvalidProjectileReferencesAndTerminalDuplicates() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            var original = (ScenarioProjectileClaim) world.claims.commands().get(2);

            for (var bad : List.of(
                new ScenarioProjectileClaim(original.header(), 1, 0, 700, 1, 100, 200),
                new ScenarioProjectileClaim(original.header(), 0, 1, 700, 1, 100, 200),
                new ScenarioProjectileClaim(original.header(), 0, 0, 701, 1, 100, 200),
                new ScenarioProjectileClaim(original.header(), 0, 0, 700, Float.NaN, 100, 200),
                new ScenarioProjectileClaim(original.header(), 0, 0, 700, -1, 100, 200)
            )) {
                assertEquals(ScenarioAdmissionOutcome.REJECTED, world.execute(replace(world, 2, bad)).outcome());
            }

            var commands = new ArrayList<>(world.claims.commands());
            var terminal = (ScenarioProjectileClaim) commands.get(3);
            var alternate = new ScenarioProjectileClaim(
                new ScenarioEventTime(2, 3, terminal.header().tick(), terminal.header().duration()),
                terminal.sequence(),
                terminal.variant(),
                terminal.shotTick(),
                terminal.terrainFactor(),
                100,
                200
            );

            assertEquals(ScenarioAdmissionOutcome.ACCEPTED, world.execute(replace(world, 3, alternate)).outcome());
            commands.add(4, alternate);
            rejected(world, world.commands(commands), "DUPLICATE_PROJECTILE_TERMINAL");
            rejected(
                world,
                replace(world, 3, new ScenarioActorClaim(new ScenarioEventTime(2, 7, 1192, 0))),
                "INVALID_TIMELINE"
            );
        }
    }

    @Test
    void rejectsMalformedTimelineOwnersDamageFallsAndLateUnsupportedFeaturesAtomically() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            var before = world.snapshot();

            for (var header : List.of(
                new ScenarioEventTime(7, 7, 0, 0),
                new ScenarioEventTime(2, 9, -1, 0),
                new ScenarioEventTime(2, 9, 120001, 0),
                new ScenarioEventTime(2, 9, 0, -1),
                new ScenarioEventTime(2, 9, 0, Integer.MAX_VALUE),
                new ScenarioEventTime(0, 9, 0, 0)
            )) {
                assertEquals(
                    ScenarioAdmissionOutcome.REJECTED,
                    world.execute(replace(world, 0, new ScenarioActorClaim(header))).outcome()
                );
            }

            for (var damage : List.of(
                new ScenarioDamageClaim(new ScenarioEventTime(2, 4, 1193, 0), -1, 1000, 0, 0),
                new ScenarioDamageClaim(new ScenarioEventTime(2, 4, 1193, 0), 1, 1000, -1, 0),
                new ScenarioDamageClaim(new ScenarioEventTime(2, 4, 1193, 0), 1, 1000, 6, 0),
                new ScenarioDamageClaim(new ScenarioEventTime(2, 4, 1193, 0), 1, 1000, 0, -1),
                new ScenarioDamageClaim(new ScenarioEventTime(2, 4, 1193, 0), 1, 1000, 0, 31)
            )) {
                rejected(world, replace(world, 4, damage), "INVALID_DAMAGE");
            }

            for (var fall : List.of(
                new ScenarioFallClaim(new ScenarioEventTime(2, 6, 1193, 0), 0, 1),
                new ScenarioFallClaim(new ScenarioEventTime(2, 6, 1193, 10), -1, 0),
                new ScenarioFallClaim(new ScenarioEventTime(2, 6, 1193, 10), 2, 1),
                new ScenarioFallClaim(new ScenarioEventTime(2, 6, 1193, 10), 0, 1151)
            )) {
                rejected(world, replace(world, 5, fall), "INVALID_FALL");
            }

            rejected(
                world,
                replace(world, 6, new ScenarioUnsupportedClaim(new ScenarioEventTime(2, 15, 1844, 0))),
                "UNSUPPORTED_FEATURE"
            );
            var events = new ArrayList<>(world.claims.commands());
            events.removeLast();
            rejected(world, world.commands(events), "INVALID_END");
            events.add(new ScenarioActorClaim(new ScenarioEventTime(2, 8, 4194, 0)));
            rejected(world, world.commands(events), "INVALID_END");
            events.add(new ScenarioActorClaim(new ScenarioEventTime(2, 8, 4194, 0)));
            rejected(world, world.commands(events), "INVALID_END");
            events.removeLast();
            events.set(events.size() - 1, new ScenarioActorClaim(new ScenarioEventTime(0, 8, 4193, 0)));
            rejected(world, world.commands(events), "INVALID_END_OWNER");
            assertEquals(before, world.snapshot());
        }
    }

    @Test
    void keepsNativeBoxClaimsUnsupportedAndAcceptsSyntheticNoBoxShotsInBothDirections() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            for (String name : List.of("native-cannon-0-0", "native-cannon-2-1")) {
                var packet = new ScenarioCodec().decode(MatchFixtures.root(name));
                var claims = new ScenarioClaimsMapper().map(packet);
                world.activeSlot = packet.shooterSlot();
                var shooter = world.members.stream()
                    .filter(member -> member.slotIndex() == packet.shooterSlot()).findFirst().orElseThrow();
                world.actors.put(shooter.sessionId(), new MatchActorState(1, 1, packet.facing(), 1000, 20));
                var context = world.admission(
                    world.context(shooter.sessionId(), shooter.admissionId()),
                    Optional.empty()
                );

                assertEquals(
                    "UNSUPPORTED_FEATURE",
                    world.action.execute(context, claims, MatchFixtures.root(name)).reason()
                );
                var scene = packet.scenario();
                var noBoxes = new GameScenario(
                    scene.slot(),
                    scene.scalar09(),
                    scene.commands().stream().filter(command -> command.header().kind() < 10).toList(),
                    scene.selectedItems(),
                    scene.effects(),
                    scene.flags(),
                    scene.power(),
                    scene.refittedSlots(),
                    scene.scalar0c()
                );
                var synthetic = new PcGamePlayScenario(
                    packet.facing(),
                    packet.shooterSlot(),
                    noBoxes,
                    packet.scalar18(),
                    packet.scalar1c(),
                    packet.tail()
                );
                byte[] syntheticRoot = new ScenarioCodec().encode(synthetic);
                assertEquals(
                    ScenarioAdmissionOutcome.ACCEPTED,
                    world.action.execute(context, new ScenarioClaimsMapper().map(synthetic), syntheticRoot).outcome()
                );
            }
        }
    }

    @Test
    void failsClearlyIfTheRequiredDigestProviderIsUnavailable() {
        var providers = Security.getProviders();

        try {
            for (var provider : providers) {
                Security.removeProvider(provider.getName());
            }

            assertThrows(IllegalStateException.class, () -> AcceptedScenario.hash(new byte[0]));
        } finally {
            for (var provider : providers) {
                Security.addProvider(provider);
            }
        }
    }

    private ScenarioClaims claims(
        ScenarioTestWorld world,
        int shooter,
        int scene,
        int facing,
        int flags,
        float power,
        List<Integer> items,
        boolean effects,
        List<Integer> refitted
    ) {
        return new ScenarioClaims(
            shooter,
            scene,
            facing,
            flags,
            power,
            items,
            effects,
            refitted,
            world.claims.commands()
        );
    }

    private ScenarioClaims replace(ScenarioTestWorld world, int index, ScenarioClaim command) {
        var commands = new ArrayList<>(world.claims.commands());
        commands.set(index, command);
        return world.commands(commands);
    }

    private void rejected(ScenarioTestWorld world, ScenarioClaims claims, String reason) {
        var result = world.execute(claims);

        assertEquals(ScenarioAdmissionOutcome.REJECTED, result.outcome());
        assertEquals(reason, result.reason());
        assertTrue(result.accepted().isEmpty());
    }
}
