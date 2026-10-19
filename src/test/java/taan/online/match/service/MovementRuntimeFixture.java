package taan.online.match.service;

import java.io.IOException;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.MoveMatchActorCommand;
import taan.online.match.service.model.PassMatchTurnCommand;

final class MovementRuntimeFixture implements AutoCloseable {
    final LiveMatchFixture live = new LiveMatchFixture();
    final MatchRuntimeFixture f = live.f;

    MovementRuntimeFixture() throws IOException {
    }

    MoveMatchActorCommand move(int dx, int dy, int facing, MatchMovementKind kind) {
        var snapshot = f.runtime.snapshot();
        var actor = snapshot.actors().get(f.room.owner);
        return new MoveMatchActorCommand(
            f.context(),
            snapshot.turn().revision(),
            snapshot.phaseRevision(),
            0,
            actor.x() + dx,
            actor.y() + dy,
            facing,
            kind
        );
    }

    boolean move(MoveMatchActorCommand command) {
        var result = f.runtime.turnWork(() -> f.runtime.movement.move(command));
        f.run();
        return result.join();
    }

    PassMatchTurnCommand pass() {
        var snapshot = f.runtime.snapshot();
        var member = snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
            .findFirst().orElseThrow();
        var actor = snapshot.actors().get(member.sessionId());
        return new PassMatchTurnCommand(
            MatchCommandContext.from(snapshot, member.sessionId()),
            snapshot.turn().revision(),
            snapshot.phaseRevision(),
            member.slotIndex(),
            actor.x(),
            actor.y(),
            actor.facing(),
            0
        );
    }

    boolean pass(PassMatchTurnCommand command) {
        var result = f.runtime.turnWork(() -> f.runtime.movement.pass(command));
        f.run();
        return result.join();
    }

    void timer(long now) {
        f.timers.now = now;
        f.timers.commands.getFirst().run();
        f.run();
    }

    @Override
    public void close() {
        live.close();
    }
}
