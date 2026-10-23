package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import taan.online.WeaponExpansionCase;
import taan.online.WeaponExpansionFixtures;
import taan.online.match.model.MatchActorState;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.WeaponMissileKey;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.MoveMatchActorCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.model.RoomMode;

class WeaponExpansionCombatTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("taan.online.WeaponExpansionFixtures#cases")
    void commitsNativeScenariosOnceAndWaitsForDistinctAcknowledgements(WeaponExpansionCase evidence) throws Exception {
        try (var f = new TeamMatchFixture(
            1,
            1,
            evidence.captain() ? RoomMode.CAPTAIN_ITEM : RoomMode.ITEM,
            evidence.weaponId()
        )) {
            f.start(true);

            if (evidence.attackKind() == 2) {
                for (int index = 0; index < 100; index++) {
                    f.pass();
                }
            }

            while (f.active().slotIndex() != evidence.shooter()) {
                f.pass();
            }

            var actor = f.snapshot().actors().get(f.active().sessionId());
            var movement = new MoveMatchActorCommand(
                f.context(f.active().sessionId()),
                f.snapshot().turn().revision(),
                f.snapshot().phaseRevision(),
                evidence.shooter(),
                actor.x(),
                actor.y(),
                evidence.facing(),
                MatchMovementKind.MOVE
            );
            assertTrue(f.scenario.matches.move(movement).toCompletableFuture().join());

            var personalItems = evidence.selectedItems().subList(
                evidence.captain() ? 1 : 0,
                evidence.selectedItems().size()
            );

            for (int item : personalItems) {
                f.activate(item);
            }

            assertEquals(evidence.selectedItems(), f.selected());

            if (!evidence.selectedItems().isEmpty()) {
                f.spend();
            }

            var before = f.snapshot();
            var shooter = f.active().sessionId();
            var packet = new ScenarioCodec().decode(WeaponExpansionFixtures.root(evidence));
            var result = f.apply(packet);
            assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome(), result.reason());
            var committed = f.snapshot().world();
            var bindings = Arrays.stream(evidence.fireBindings().split(";")).map(row -> row.split(":"))
                .collect(Collectors.toMap(
                    columns -> Integer.parseInt(columns[0]),
                    columns -> new WeaponMissileKey(evidence.weaponId(), Integer.parseInt(columns[2]))
                ));
            assertEquals(bindings, result.committed().orElseThrow().scenario().journal().missileDefinitions());
            assertEquals(before.world().revision() + 1, committed.revision());
            assertEquals(
                evidence.damage(),
                before.actors().values().stream().mapToInt(MatchActorState::hp).sum()
                    - committed.actors().values().stream().mapToInt(MatchActorState::hp).sum()
            );
            assertEquals(1, committed.combatStatistics().acceptedShots(shooter));
            assertEquals(ScenarioApplicationOutcome.DUPLICATE, f.apply(packet).outcome());
            assertEquals(committed, f.snapshot().world());
            assertTrue(f.snapshot().attacks().isEmpty());

            assertTrue(f.ack(shooter));
            assertFalse(f.ack(shooter));
            assertEquals(1, f.snapshot().completion().orElseThrow().awaitingSessions().size());
            f.acknowledge();

            if (f.snapshot().phase() == MatchPhase.COMPLETION) {
                f.scenario.matchTimers.now = f.snapshot().completion().orElseThrow().settleNotBeforeNanos();
                f.scenario.matchTimers.advanceSeconds(0);
            }

            assertEquals(MatchPhase.ACTION, f.snapshot().phase());
            assertEquals(before.turn().revision() + 1, f.snapshot().turn().revision());
            assertTrue(f.outputs.values().stream().allMatch(output -> output.closed.isEmpty()));
        }
    }
}
