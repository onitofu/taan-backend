package taan.online.match.service;

import static taan.online.logging.LogField.admissionId;
import static taan.online.logging.LogField.awaitingCount;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.mapId;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.round;
import static taan.online.logging.LogField.scenarioHash;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;
import static taan.online.logging.LogField.turnRevision;
import static taan.online.logging.LogField.windRaw;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.logging.WarningEvent;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.Match;
import taan.online.match.model.MatchCompletion;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.ConfirmMatchPositionCommand;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchDeadline;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.MatchTurnToken;
import taan.online.match.service.model.MatchTurnTransition;
import taan.online.match.service.model.PreparedScenario;
import taan.online.match.service.model.ScenarioFallClaim;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchProgressToken;

final class MatchTurnExecution {
    private static final EventLogger LOG = EventLogger.forClass(MatchTurnExecution.class);
    private final MatchRuntime owner;
    private final Match match;
    private final Object admission;
    private final MatchBootstrapSettings settings;
    private final MatchScheduler scheduler;
    private final LongSupplier clock;
    private final WarningLogLimiter warnings;

    MatchTurnExecution(
        MatchRuntime owner,
        Match match,
        Object admission,
        MatchBootstrapSettings settings,
        MatchScheduler scheduler,
        LongSupplier clock,
        WarningLogLimiter warnings
    ) {
        this.owner = owner;
        this.match = match;
        this.admission = admission;
        this.settings = settings;
        this.scheduler = scheduler;
        this.clock = clock;
        this.warnings = warnings;
    }

    boolean position(ConfirmMatchPositionCommand command) {
        boolean valid = match.confirmsPosition(
            command.context().sessionId(),
            command.slot(),
            command.x(),
            command.y(),
            command.facing()
        );

        if (valid) {
            LOG.log(MatchLogEvent.POSITION_CONFIRMED, matchId(match.id().value()), reason("UNCHANGED"));
        } else {
            warnings.warn(WarningEvent.FRAME_REJECTED, "INVALID_POSITION_CONFIRMATION");
        }

        return valid;
    }

    void firstTurnIssued() {
        LOG.log(
            MatchLogEvent.FIRST_TURN_ISSUED,
            matchId(match.id().value()),
            roomId(match.roomId().value()),
            channelId(match.channelId()),
            mapId(match.definitions().map().mapId()),
            slotIndex(match.turn().activeSlot()),
            turnRevision(match.turn().revision())
        );
    }

    Optional<MatchCompletion> completion(PreparedScenario prepared) {
        return settings.executionMode() == MatchExecutionMode.CANNON_1V1
            ? Optional.of(prepare(prepared)) : Optional.empty();
    }

    MatchCompletion prepare(PreparedScenario prepared) {
        var accepted = prepared.scenario();
        var sender = accepted.sender();
        var journal = accepted.journal();
        long fallEnd = journal.events().stream().filter(ScenarioFallClaim.class::isInstance)
            .mapToLong(event -> event.header().tick() + event.header().duration() + 3).max().orElse(0);
        long now = clock.getAsLong();
        return new MatchCompletion(
            sender.sessionId(),
            sender.admissionId(),
            accepted.hash(),
            accepted.turnRevision(),
            prepared.world().revision(),
            owner.snapshot().loadedSessions(),
            Math.max(
                now + TimeUnit.MILLISECONDS.toNanos(fallEnd),
                owner.snapshot().movements().get(sender.sessionId())
                .fall().map(value -> value.endsAtNanos()).orElse(now)
            ),
            now + TimeUnit.MILLISECONDS.toNanos(journal.endTick()) + settings.completionGraceNanos(),
            owner.snapshot().phaseRevision()
        );
    }

    boolean acknowledge(ConfirmScenarioCommand command) {
        boolean accepted;

        synchronized (admission) {
            accepted = valid(command) && match.acknowledgeScenario(command.context().sessionId());

            if (accepted) {
                owner.refresh();
            }
        }

        LOG.log(
            accepted ? MatchLogEvent.SCENARIO_ACKNOWLEDGED : MatchLogEvent.SCENARIO_ACK_IGNORED,
            matchId(command.context().matchId().value()),
            sessionId(command.context().sessionId()),
            admissionId(command.context().admissionId()),
            turnRevision(command.turnRevision())
        );

        if (accepted) {
            finish();
        }

        return accepted;
    }

    private boolean valid(ConfirmScenarioCommand command) {
        var snapshot = owner.snapshot();

        if (!owner.admits(command.context()) || snapshot.phase() != MatchPhase.COMPLETION
            || command.turnRevision() != snapshot.turn().revision()) {
            return false;
        }

        var pending = snapshot.completion().orElseThrow();

        if (clock.getAsLong() - pending.deadlineNanos() >= 0
            || command.scenarioSlot() != snapshot.turn().activeSlot()) {
            return false;
        }

        return command.fingerprint().isPresent()
            ? command.worldRevision() == pending.worldRevision()
                && command.fingerprint().orElseThrow().equals(pending.fingerprint())
            : command.context().sessionId().equals(pending.shooterSessionId())
                && (command.phaseRevision() == pending.actionPhaseRevision()
                    || command.phaseRevision() == -1 && command.worldRevision() == pending.worldRevision() - 1);
    }

    void arm() {
        var snapshot = owner.snapshot();
        var pending = snapshot.completion().orElseThrow();
        long deadline = pending.awaitingSessions().isEmpty()
            ? pending.settleNotBeforeNanos() : pending.deadlineNanos();
        var token = token(snapshot);
        scheduler.cancel(snapshot.id().value());
        scheduler.schedule(
            new MatchDeadline(owner.progress(), deadline),
            () -> enqueue(token, Optional.of(pending.fingerprint())),
            () -> unavailable(token)
        );
    }

    void enqueue(RoomMatchProgressToken progress) {
        var snapshot = owner.snapshot();
        enqueue(
            new MatchTurnToken(progress.match(), snapshot.turn().revision(), progress.revision()),
            snapshot.completion().map(MatchCompletion::fingerprint)
        );
    }

    private void enqueue(MatchTurnToken token, Optional<String> fingerprint) {
        owner.turnDeadlineExpired(token, fingerprint);
    }

    boolean expired(MatchTurnToken token, Optional<String> fingerprint) {
        var snapshot = owner.snapshot();

        if (!current(token) || !snapshot.completion().map(MatchCompletion::fingerprint).equals(fingerprint)) {
            return false;
        }

        if (snapshot.phase() == MatchPhase.ACTION && clock.getAsLong() - snapshot.deadlineNanos() >= 0) {
            LOG.log(MatchLogEvent.TURN_TIMEOUT, matchId(snapshot.id().value()), turnRevision(token.turnRevision()));
            owner.movement.timeout();
            return true;
        }

        if (snapshot.phase() == MatchPhase.SETTLING) {
            return owner.movement.expired();
        }

        if (snapshot.phase() != MatchPhase.COMPLETION) {
            return false;
        }

        var pending = snapshot.completion().orElseThrow();

        if (!pending.awaitingSessions().isEmpty() && clock.getAsLong() - pending.deadlineNanos() >= 0) {
            LOG.log(
                MatchLogEvent.COMPLETION_TIMEOUT,
                matchId(snapshot.id().value()),
                scenarioHash(pending.fingerprint()),
                awaitingCount(pending.awaitingSessions().size())
            );
            owner.outcomes.timedOut(pending.awaitingSessions());
            return true;
        }

        finish();
        return true;
    }

    void finish() {
        var pending = owner.snapshot().completion().orElseThrow();

        if (!owner.running() || !pending.awaitingSessions().isEmpty()) {
            return;
        }

        if (clock.getAsLong() - pending.settleNotBeforeNanos() < 0) {
            arm();
            return;
        }

        boolean walking;

        synchronized (admission) {
            if (!owner.running()) {
                return;
            }

            walking = match.movements().values().stream().anyMatch(value -> value.fall().isPresent());
            var before = owner.snapshot();
            match.settleMovement(clock.getAsLong());
            owner.refresh();
            MatchOutcomeExecution.decided(before, owner.snapshot());
        }

        if (walking) {
            owner.movement.settled();
        }

        LOG.log(MatchLogEvent.SCENARIO_COMPLETED, matchId(match.id().value()));
        advance(true);
    }

    void advance(boolean turnEndRequired) {
        MatchTurnTransition transition = null;
        boolean terminal;

        synchronized (admission) {
            if (!owner.running()) {
                return;
            }

            terminal = match.resolution().pendingOutcome().isPresent();
            int previous = match.turn().activeSlot();

            if (!terminal) {
                if (turnEndRequired) {
                    owner.items.loot.offerBonus();
                }

                var before = owner.snapshot();
                match.nextTurn(clock.getAsLong(), settings.actionTimeoutNanos());
                owner.refresh();
                owner.resetScenario();
                MatchEnergyLog.turn(before, owner.snapshot());
                transition = new MatchTurnTransition(previous, owner.snapshot(), turnEndRequired);
            }
        }

        if (terminal) {
            owner.outcomes.finish();
        } else {
            publish(transition);
        }
    }

    private void publish(MatchTurnTransition transition) {
        var snapshot = transition.snapshot();
        var token = token(snapshot);
        LOG.log(
            MatchLogEvent.TURN_ISSUED,
            matchId(snapshot.id().value()),
            slotIndex(snapshot.turn().activeSlot()),
            turnRevision(snapshot.turn().revision()),
            round(snapshot.turn().round()),
            windRaw(snapshot.turn().windRaw())
        );

        try {
            scheduler.cancel(snapshot.id().value());
            scheduler.schedule(
                new MatchDeadline(owner.progress(), snapshot.deadlineNanos()),
                () -> enqueue(token, Optional.empty()),
                () -> unavailable(token)
            );
            var deliveries = new ArrayList<CompletableFuture<Void>>();

            for (var recipient : owner.recipients()) {
                var member = snapshot.participants().stream().filter(p -> p.sessionId().equals(recipient.sessionId()))
                    .findFirst().orElseThrow();
                var context = new MatchCommandContext(
                    snapshot.id(),
                    snapshot.roomId(),
                    snapshot.roomGeneration(),
                    snapshot.channelId(),
                    member.sessionId(),
                    member.admissionId()
                );

                if (owner.admits(context)) {
                    deliveries.add(recipient.output().nextTurn(transition).toCompletableFuture());
                }
            }

            CompletableFuture.allOf(deliveries.toArray(CompletableFuture[]::new)).handle(LogContext.propagate(
                (ignored, failure) -> {
                    if (failure != null) {
                        owner.turnWork(
                            () -> deliveryFailed(token, failure),
                            () -> deliveryFailed(token, failure)
                        );
                    }

                    return null;
                }
            ));
        } catch (RuntimeException failure) {
            deliveryFailed(token, failure);
        }
    }

    private boolean deliveryFailed(MatchTurnToken token, Throwable failure) {
        if (!owner.running() || token.turnRevision() != owner.snapshot().turn().revision()) {
            return false;
        }

        LOG.failure(MatchLogEvent.TURN_DELIVERY_FAILED, failure, matchId(token.match().matchId()));
        owner.terminate(RoomMatchEndReason.TURN_DELIVERY_FAILED);
        return true;
    }

    private void unavailable(MatchTurnToken token) {
        if (current(token)) {
            warnings.warn(WarningEvent.LIMIT_EXCEEDED, "MATCH_SCHEDULER_UNAVAILABLE");
            owner.terminate(RoomMatchEndReason.PREPARATION_FAILED);
        }
    }

    private MatchTurnToken token(MatchSnapshot snapshot) {
        return new MatchTurnToken(owner.progress().match(), snapshot.turn().revision(), snapshot.phaseRevision());
    }

    private boolean current(MatchTurnToken token) {
        return owner.running() && token.equals(token(owner.snapshot()));
    }
}
