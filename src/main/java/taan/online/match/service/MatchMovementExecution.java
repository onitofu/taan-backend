package taan.online.match.service;

import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;
import static taan.online.logging.LogField.turnRevision;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.Match;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.MatchTurnClosureReason;
import taan.online.match.model.ScenarioWorldException;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchDeadline;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.MatchMovementNotice;
import taan.online.match.service.model.MatchTurnToken;
import taan.online.match.service.model.MoveMatchActorCommand;
import taan.online.match.service.model.PassMatchTurnCommand;
import taan.online.room.service.model.RoomMatchEndReason;

final class MatchMovementExecution {
    private static final EventLogger LOG = EventLogger.forClass(MatchMovementExecution.class);
    private final MatchRuntime owner;
    private final Match match;
    private final Object admission;
    private final LongSupplier clock;
    private final MatchScheduler scheduler;

    MatchMovementExecution(
        MatchRuntime owner,
        Match match,
        Object admission,
        LongSupplier clock,
        MatchScheduler scheduler
    ) {
        this.owner = owner;
        this.match = match;
        this.admission = admission;
        this.clock = clock;
        this.scheduler = scheduler;
    }

    boolean move(MoveMatchActorCommand command) {
        return accept(
            command.context(),
            command.turnRevision(),
            command.phaseRevision(),
            new MatchMovementNotice(
                command.kind(),
                command.slot(),
                command.x(),
                command.y(),
                command.facing(),
                0
            )
        );
    }

    boolean pass(PassMatchTurnCommand command) {
        return accept(
            command.context(),
            command.turnRevision(),
            command.phaseRevision(),
            new MatchMovementNotice(
                MatchMovementKind.PASS_TURN,
                command.slot(),
                command.x(),
                command.y(),
                command.facing(),
                command.scalar20()
            )
        );
    }

    private boolean accept(MatchCommandContext context, long revision, long phaseRevision, MatchMovementNotice notice) {
        if (!owner.live() || !owner.admits(context) || revision != match.turn().revision()) {
            return ignored(context);
        }

        var member = match.participants().stream().filter(p -> p.sessionId().equals(context.sessionId()))
            .findFirst().orElseThrow();

        if (member.slotIndex() != notice.slot() || member.slotIndex() != match.turn().activeSlot()) {
            if (notice.kind() == MatchMovementKind.SET_POS && match.confirmsPosition(
                context.sessionId(),
                notice.slot(),
                notice.x(),
                notice.y(),
                notice.facing()
            )) {
                return true;
            }

            return ignored(context);
        }

        if (notice.kind() == MatchMovementKind.PASS_TURN && !owner.items.canCloseTurn()) {
            return false;
        }

        long now = clock.getAsLong();

        if (match.phase() == MatchPhase.ACTION && match.expired(now)) {
            timeout();
        }

        boolean closing = match.phase() == MatchPhase.SETTLING;
        long expectedPhase = closing ? match.closure().orElseThrow().actionPhaseRevision() : match.phaseRevision();

        if (phaseRevision != expectedPhase && phaseRevision != match.phaseRevision()
            || match.phase() != MatchPhase.ACTION && !closing
            || closing && now - match.closure().orElseThrow().stopDrainUntilNanos() >= 0
            || match.actors().get(context.sessionId()).dead()) {
            return ignored(context);
        }

        try {
            synchronized (admission) {
                if (!owner.admits(context)) {
                    return ignored(context);
                }

                match.move(context.sessionId(), notice.x(), notice.y(), notice.facing(), now);
                owner.refresh();
            }
        } catch (ScenarioWorldException failure) {
            LOG.log(MatchLogEvent.MOVEMENT_REJECTED, matchId(match.id().value()), reason(failure.getMessage()));
            owner.terminate(RoomMatchEndReason.INVALID_MOVEMENT);
            return false;
        }

        LOG.log(
            MatchLogEvent.MOVEMENT_ACCEPTED,
            matchId(match.id().value()),
            sessionId(context.sessionId()),
            slotIndex(notice.slot()),
            turnRevision(revision)
        );
        relay(notice);

        if (notice.kind() == MatchMovementKind.PASS_TURN && !closing) {
            synchronized (admission) {
                if (!owner.running()) {
                    return true;
                }

                match.closeTurn(MatchTurnClosureReason.PASS_TURN, now);
                owner.refresh();
            }

            LOG.log(MatchLogEvent.TURN_PASSED, matchId(match.id().value()), turnRevision(revision));
            finish();
        } else if (closing) {
            arm();
        }

        return true;
    }

    private boolean ignored(MatchCommandContext context) {
        LOG.log(MatchLogEvent.MOVEMENT_IGNORED, matchId(context.matchId().value()), sessionId(context.sessionId()));
        return false;
    }

    void timeout() {
        if (!owner.items.canCloseTurn()) {
            return;
        }

        synchronized (admission) {
            if (!owner.running() || match.phase() != MatchPhase.ACTION) {
                return;
            }

            match.closeTurn(MatchTurnClosureReason.ACTION_TIMEOUT, clock.getAsLong());
            owner.refresh();
        }

        LOG.log(MatchLogEvent.TURN_STOPPING, matchId(match.id().value()), turnRevision(match.turn().revision()));
        deliver(null);
        arm();
    }

    boolean expired() {
        if (clock.getAsLong() - match.settleAt() < 0) {
            arm();
            return false;
        }

        finish();
        return true;
    }

    private void finish() {
        synchronized (admission) {
            if (!owner.running()) {
                return;
            }

            if (clock.getAsLong() - match.settleAt() >= 0) {
                match.settleMovement(clock.getAsLong());
                owner.refresh();
            }
        }

        if (clock.getAsLong() - match.settleAt() < 0) {
            arm();
            return;
        }

        settled();
        owner.turns.advance(match.closure().orElseThrow().reason() != MatchTurnClosureReason.ACTION_TIMEOUT);
    }

    void settled() {
        var actor = match.participants().stream().filter(p -> p.slotIndex() == match.turn().activeSlot())
            .findFirst().orElseThrow();
        var position = match.actors().get(actor.sessionId());
        relay(new MatchMovementNotice(
            MatchMovementKind.SET_POS,
            actor.slotIndex(),
            position.x(),
            position.y(),
            position.facing(),
            0
        ));
        LOG.log(MatchLogEvent.MOVEMENT_SETTLED, matchId(match.id().value()), slotIndex(actor.slotIndex()));
    }

    private void arm() {
        if (!owner.running()) {
            return;
        }

        var token = new MatchTurnToken(owner.progress().match(), match.turn().revision(), match.phaseRevision());
        scheduler.cancel(match.id().value());
        scheduler.schedule(
            new MatchDeadline(owner.progress(), match.settleAt()),
            () -> owner.turnDeadlineExpired(token, Optional.empty()),
            () -> owner.turnWork(() -> failed(token.turnRevision(), new IllegalStateException("SCHEDULER_UNAVAILABLE")))
        );
    }

    private void relay(MatchMovementNotice notice) {
        deliver(notice);
    }

    private void deliver(MatchMovementNotice notice) {
        long revision = match.turn().revision();

        try {
            for (var recipient : owner.recipients()) {
                var member = match.participants().stream().filter(p -> p.sessionId().equals(recipient.sessionId()))
                    .findFirst().orElseThrow();
                var context = new MatchCommandContext(
                    match.id(),
                    match.roomId(),
                    match.roomGeneration(),
                    match.channelId(),
                    member.sessionId(),
                    member.admissionId()
                );

                if (owner.admits(context) && (notice == null || member.slotIndex() != notice.slot())) {
                    CompletionStage<Void> delivery = notice == null
                        ? recipient.output().stopTurn(match.turn().activeSlot()) : recipient.output().movement(notice);
                    delivery.handle(LogContext.propagate((ignored, failure) -> {
                        if (failure != null) {
                            owner.turnWork(() -> failed(revision, failure), () -> failed(revision, failure));
                        }

                        return null;
                    }));
                }
            }
        } catch (RuntimeException failure) {
            failed(revision, failure);
        }
    }

    private boolean failed(long revision, Throwable failure) {
        if (!owner.running() || match.turn().revision() != revision) {
            return false;
        }

        LOG.failure(MatchLogEvent.MOVEMENT_DELIVERY_FAILED, failure, matchId(match.id().value()));
        owner.terminate(RoomMatchEndReason.MOVEMENT_DELIVERY_FAILED);
        return true;
    }
}
