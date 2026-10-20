package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.MatchStateTestAccess;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.MatchWorldState;
import taan.online.match.model.ScenarioCommitReceipt;
import taan.online.match.model.TerrainLayerDefinition;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchMovementScenarioTest {
    @Test
    void retainsAWalkingFallNotReplacedByTheScenarioAndSettlesItBeforeTheNextTurn() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            int grounded = f.runtime.snapshot().actors().get(f.room.owner).y();
            assertTrue(m.move(m.move(0, -200, 0, MatchMovementKind.MOVE)));
            var before = f.runtime.snapshot().world();
            f.match.settleMovement(1);
            assertSame(before, f.match.world());
            var command = shot(m, List.of(), false);
            var result = f.runtime.applyScenario(command);
            f.run();
            assertEquals(ScenarioApplicationOutcome.APPLIED, result.join().outcome());
            var pending = f.runtime.snapshot().completion().orElseThrow();
            var move = m.move(0, 0, 0, MatchMovementKind.MOVE);
            assertFalse(m.move(move));
            var wrongPhase = new ConfirmScenarioCommand(
                f.context(),
                pending.turnRevision(),
                pending.worldRevision() - 1,
                Optional.empty(),
                0,
                999
            );
            var ignored = f.runtime.confirmScenario(wrongPhase);
            f.run();
            assertFalse(ignored.join());
            f.runtime.confirmScenario(m.live.ack(0, false));
            f.runtime.confirmScenario(m.live.ack(1, false));
            f.run();
            assertEquals(MatchPhase.COMPLETION, f.runtime.snapshot().phase());
            m.timer(pending.settleNotBeforeNanos());
            assertEquals(MatchPhase.ACTION, f.runtime.snapshot().phase());
            assertEquals(grounded, f.runtime.snapshot().actors().get(f.room.owner).y());
            assertEquals(MatchMovementKind.SET_POS, f.guest.movements.getLast().kind());
        }
    }

    @Test
    void canonicalRefittingAndSelfDeathReplaceTheWalkingFallWithoutApplyingItTwice() throws Exception {
        for (boolean dead : new boolean[]{false, true}) {
            try (var m = new MovementRuntimeFixture()) {
                var f = m.f;
                assertTrue(m.move(m.move(0, -20, 0, MatchMovementKind.MOVE)));
                var result = f.runtime.applyScenario(shot(m, dead ? List.of() : List.of(0), dead));
                f.run();
                assertEquals(ScenarioApplicationOutcome.APPLIED, result.join().outcome());
                assertTrue(f.match.movements().get(f.room.owner).fall().isEmpty());
                var publication = result.join().committed().orElseThrow();
                assertEquals(f.match.movements(), f.runtime.snapshot().movements());
                assertEquals(f.match.movements(), publication.snapshot().movements());
                assertSame(f.runtime.snapshot(), publication.snapshot());
                assertEquals(f.match.world(), publication.snapshot().world());
                assertEquals(f.match.resolution(), publication.snapshot().resolution());
                assertEquals(f.match.attacks(), publication.snapshot().attacks());
                assertEquals(f.match.loot(), publication.snapshot().loot());
                var pending = f.runtime.snapshot().completion().orElseThrow();
                f.runtime.confirmScenario(m.live.ack(0, false));
                f.runtime.confirmScenario(m.live.ack(1, false));
                f.run();
                m.timer(pending.settleNotBeforeNanos());
                assertEquals(dead ? RoomMatchEndReason.PARTICIPANT_ELIMINATED : null, f.callbacks.reason);
                var committed = f.match.world();
                f.match.settleMovement(f.timers.now);
                assertSame(committed, f.match.world());
            }
        }
    }

    @Test
    void stoppingAfterTheCompletionClockCheckPreventsTheFinalMutation() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            f.runtime.applyScenario(m.live.shot(false, false));
            f.run();
            f.runtime.confirmScenario(m.live.ack(0, false));
            f.run();
            var checks = new AtomicInteger();
            f.onClock = () -> {
                if (checks.incrementAndGet() == 2) {
                    f.runtime.stop();
                }
            };
            f.runtime.confirmScenario(m.live.ack(1, false));
            f.run();
            assertEquals(1, f.owner.turns.size());
        }
    }

    @Test
    void aWalkingBodyDiesIfItsPreviouslyTrackedLandingSupportWasDestroyed() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            assertTrue(m.move(m.move(0, -20, 0, MatchMovementKind.MOVE)));
            var before = f.match.world();
            var member = f.match.participants().getFirst();
            var empty = before.terrain().stream().map(layer -> new TerrainLayerDefinition(
                layer.left(),
                layer.top(),
                layer.width(),
                layer.height(),
                new byte[layer.width() * layer.height()]
            )).toList();
            var destroyed = new MatchWorldState(
                before.revision() + 1,
                before.actors(),
                empty,
                Map.of(),
                before.processedDeaths(),
                Optional.of(new ScenarioCommitReceipt(
                    "a".repeat(64),
                    member.sessionId(),
                    member.admissionId(),
                    f.match.turn().revision(),
                    before.revision() + 1
                ))
            );
            assertTrue(MatchStateTestAccess.commit(f.match, before.revision(), f.match.turn().revision(), destroyed));
            var movement = f.match.movements().get(member.sessionId());
            f.match.settleMovement(movement.fall().orElseThrow().endsAtNanos());
            assertEquals(0, f.match.actors().get(member.sessionId()).hp());
            assertTrue(f.match.world().processedDeaths().contains(member.sessionId()));
        }
    }

    private ApplyScenarioCommand shot(MovementRuntimeFixture m, List<Integer> refitted, boolean selfDeath)
        throws Exception {
        var command = m.live.shot(false, false);
        var packet = new ScenarioCodec().decode(command.root());
        var old = packet.scenario();
        var events = new ArrayList<>(old.commands());

        if (selfDeath) {
            events.add(
                events.size() - 1,
                new ScenarioDamageCommand(
                    new ScenarioCommandHeader(0, 4, 995, 0),
                    10000,
                    0,
                    0,
                    0
                )
            );
        }

        var scene = new PcGamePlayScenario(
            packet.facing(),
            packet.shooterSlot(),
            new GameScenario(
                old.slot(),
                old.scalar09(),
                events,
                old.selectedItems(),
                old.effects(),
                old.flags(),
                old.power(),
                refitted,
                old.scalar0c()
            ),
            packet.scalar18(),
            packet.scalar1c(),
            packet.tail()
        );
        return new ApplyScenarioCommand(
            command.context(),
            command.turnRevision(),
            command.worldRevision(),
            new ScenarioClaimsMapper().map(scene),
            new ScenarioCodec().encode(scene),
            m.f.runtime.snapshot().phaseRevision()
        );
    }
}
