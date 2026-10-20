package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.ConfirmMatchLoadingCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.MoveMatchActorCommand;
import taan.online.match.service.model.StartMatchCommand;

class MatchInitialReadinessTest {
    @Test
    void requiresTheActionPhaseEvenWhenTheClientHasAlreadyReceivedItsFirstTurn() {
        try (var f = new MatchRuntimeFixture(8, MatchExecutionMode.CLIENT_SCENARIO)) {
            f.activate();
            f.runtime.submit(new StartMatchCommand(f.token));
            f.run();
            f.timers.delayCompletions = true;

            for (var member : f.runtime.snapshot().participants()) {
                f.runtime.submit(new ConfirmMatchLoadingCommand(
                    MatchCommandContext.from(f.runtime.snapshot(), member.sessionId()),
                    member.slotIndex(),
                    member.spawn().settledX(),
                    member.spawn().settledY()
                ));
                f.run();
            }

            assertEquals(1, f.owner.turns.size());
            assertEquals(1, f.guest.turns.size());
            assertEquals(MatchPhase.SYNCHRONIZING, f.runtime.snapshot().phase());
            var early = movement(f);
            var beforeReady = f.runtime.turnWork(() -> f.runtime.movement.move(early));
            f.run();
            assertFalse(beforeReady.join());
            assertTrue(f.guest.movements.isEmpty());

            f.timers.complete();
            f.run();
            assertEquals(MatchPhase.ACTION, f.runtime.snapshot().phase());
            var stale = f.runtime.turnWork(() -> f.runtime.movement.move(early));
            f.run();
            assertFalse(stale.join());
            var current = movement(f);
            var accepted = f.runtime.turnWork(() -> f.runtime.movement.move(current));
            f.run();
            assertTrue(accepted.join());
            assertEquals(1, f.guest.movements.size());
        }
    }

    private MoveMatchActorCommand movement(MatchRuntimeFixture f) {
        var s = f.runtime.snapshot();
        var actor = s.actors().get(f.room.owner);
        return new MoveMatchActorCommand(
            f.context(),
            s.turn().revision(),
            s.phaseRevision(),
            0,
            actor.x(),
            actor.y(),
            actor.facing(),
            MatchMovementKind.MOVE
        );
    }
}
