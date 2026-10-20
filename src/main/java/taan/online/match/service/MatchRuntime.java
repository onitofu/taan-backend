package taan.online.match.service;

import static taan.online.logging.LogField.commandType;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.matchRevision;
import static taan.online.logging.LogField.reason;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import taan.online.logging.EventLogger;
import taan.online.logging.WarningEvent;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.Match;
import taan.online.match.model.MatchCompletion;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.CommittedScenario;
import taan.online.match.service.model.ConfirmMatchLoadingCommand;
import taan.online.match.service.model.ConfirmMatchPositionCommand;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.match.service.model.MatchCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchDeadlineExpiredCommand;
import taan.online.match.service.model.MatchDeliveryCompletedCommand;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.MatchStateCommand;
import taan.online.match.service.model.MatchTurnToken;
import taan.online.match.service.model.PreparedScenario;
import taan.online.match.service.model.ReportMatchEnemiesCommand;
import taan.online.match.service.model.ScenarioApplicationResult;
import taan.online.match.service.model.StartMatchCommand;
import taan.online.match.service.model.TerminateMatchCommand;
import taan.online.room.service.RoomMatchCallbacks;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchProgressToken;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchToken;

final class MatchRuntime {
    private static final EventLogger LOG = EventLogger.forClass(MatchRuntime.class);

    private final Match match;
    final MatchScenarioExecution scenarios;
    final MatchTurnExecution turns;
    final MatchMovementExecution movement;
    final MatchItemExecution items;
    final MatchOutcomeExecution outcomes;
    private final MatchMailbox mailbox;
    private final MatchAdmissionAccess access;
    private final MatchBootstrapSettings settings;
    private final MatchScheduler scheduler;
    private final LongSupplier clock;
    private final WarningLogLimiter warnings;
    private final Map<UUID, MatchReportBudget> reportBudgets = new HashMap<>();
    private final Object admission = new Object();
    private volatile MatchSnapshot snapshot;
    private volatile boolean closed;
    private volatile long publishedRevision = -1;
    private boolean startPublished;
    private RoomMatchCallbacks callbacks;
    private List<MatchSubscription> recipients = List.of();
    private volatile CompletionStage<Void> startDelivery = CompletableFuture.completedFuture(null);

    MatchRuntime(
        Match match,
        MatchBootstrapSettings settings,
        MatchScheduler scheduler,
        LongSupplier clock,
        WarningLogLimiter warnings,
        MatchCommandExecutor executor,
        int capacity,
        MatchAdmissionAccess access
    ) {
        this.match = match;
        this.access = access;
        this.settings = settings;
        this.scheduler = scheduler;
        this.clock = clock;
        this.warnings = warnings;
        mailbox = new MatchMailbox(executor, capacity);
        snapshot = MatchSnapshot.from(match);
        scenarios = new MatchScenarioExecution(this, access);
        turns = new MatchTurnExecution(this, match, admission, settings, scheduler, clock, warnings);
        outcomes = new MatchOutcomeExecution(this, match, admission, clock);
        items = new MatchItemExecution(this, match, admission, clock, settings.lootPolicy());
        movement = new MatchMovementExecution(this, match, admission, clock, scheduler);
    }

    CompletableFuture<ScenarioApplicationResult> applyScenario(ApplyScenarioCommand command) {
        return mailbox.submit(
            () -> scenarios.execute(command),
            ScenarioApplicationResult.ignored("CANCELLED"),
            () -> rejected(new TerminateMatchCommand(command.context(), RoomMatchEndReason.PREPARATION_FAILED))
        );
    }

    boolean scenarioCurrent(ApplyScenarioCommand command) {
        return settings.executionMode() != MatchExecutionMode.PREVIEW && admits(command.context())
            && !snapshot.resolution().exits().containsKey(command.context().sessionId());
    }

    boolean commitScenario(ApplyScenarioCommand command, PreparedScenario prepared, MatchSnapshot publication) {
        synchronized (admission) {
            if (!scenarioCurrent(command) || match.expired(clock.getAsLong())
                || !match.commitScenario(
                    prepared.baseWorldRevision(),
                    command.turnRevision(),
                    prepared.world(),
                    publication.completion()
                )) {
                return false;
            }

            items.clear();
            snapshot = publication;
            publishedRevision = publication.phaseRevision();
            return true;
        }
    }

    CompletionStage<Void> publishScenario(CommittedScenario scenario, ScenarioApplicationResult result) {
        if (settings.executionMode() == MatchExecutionMode.CANNON_1V1) {
            turns.arm();
        } else {
            new MatchBootstrapDelivery(snapshot, recipients, this, scheduler, progress(), warnings).dispatch(null);
        }
        return scenarios.relay().deliver(result, snapshot, recipients);
    }

    void scenarioDeliveryFailed(CommittedScenario scenario, Throwable failure) {
        mailbox.submit(
            () -> scenarios.deliveryFailed(scenario, failure),
            () -> scenarios.deliveryFailed(scenario, failure)
        );
    }

    boolean live() {
        return settings.executionMode() == MatchExecutionMode.CANNON_1V1;
    }

    CompletableFuture<Boolean> confirmScenario(ConfirmScenarioCommand command) {
        return turnWork(() -> settings.executionMode() == MatchExecutionMode.CANNON_1V1 && turns.acknowledge(command));
    }

    CompletableFuture<Boolean> turnWork(BooleanSupplier command) {
        return turnWork(command, () -> terminate(RoomMatchEndReason.PREPARATION_FAILED));
    }

    CompletableFuture<Boolean> turnWork(BooleanSupplier command, Runnable rejected) {
        return mailbox.submit(
            () -> {
                try {
                    return command.getAsBoolean();
                } catch (RuntimeException failure) {
                    LOG.failure(MatchLogEvent.COMMAND_FAILED, failure, matchId(snapshot.id().value()));
                    terminate(RoomMatchEndReason.PREPARATION_FAILED);
                    return false;
                }
            },
            rejected
        );
    }

    void turnDeadlineExpired(MatchTurnToken token, Optional<String> fingerprint) {
        turnWork(() -> turns.expired(token, fingerprint));
    }

    Optional<MatchCompletion> completion(PreparedScenario prepared) {
        return settings.executionMode() == MatchExecutionMode.CANNON_1V1
            ? Optional.of(turns.prepare(prepared)) : Optional.empty();
    }

    List<MatchSubscription> recipients() {
        return recipients;
    }

    void refresh() {
        snapshot = MatchSnapshot.from(match);
        publishedRevision = snapshot.phaseRevision();
    }

    void resetScenario() {
        scenarios.reset();
    }

    boolean running() {
        return !closed;
    }

    MatchSnapshot snapshot() {
        return snapshot;
    }

    RoomMatchProgressToken progress() {
        return new RoomMatchProgressToken(token(), snapshot.phaseRevision());
    }

    private RoomMatchToken token() {
        return new RoomMatchToken(snapshot.roomId(), snapshot.roomGeneration(), snapshot.id().value());
    }

    boolean activate(RoomMatchCallbacks value, List<MatchSubscription> outputs) {
        synchronized (admission) {
            if (closed || snapshot.phase() != MatchPhase.RESERVED || outputs.size() != snapshot.participants().size()) {
                return false;
            }

            callbacks = value;
            recipients = List.copyOf(outputs);
            return true;
        }
    }

    boolean admits(MatchCommandContext context) {
        return !closed && token().equals(context.token()) && snapshot.channelId() == context.channelId()
            && snapshot.participants().stream().anyMatch(
                p -> p.sessionId().equals(context.sessionId()) && p.admissionId().equals(context.admissionId())
                    && access.current(context, p)
            );
    }

    CompletableFuture<Boolean> submit(MatchCommand command) {
        return mailbox.submit(() -> execute(command), () -> rejected(command));
    }

    private boolean execute(MatchCommand command) {
        long started = clock.getAsLong();

        try {
            if (command instanceof StartMatchCommand && callbacks != null && !callbacks.startCurrent(token())) {
                terminate(RoomMatchEndReason.PREPARATION_FAILED);
                return false;
            }

            if (command instanceof TerminateMatchCommand end) {
                synchronized (admission) {
                    if (closed || !admits(end.context())) {
                        return false;
                    }
                }

                return outcomes.end(end);
            }

            RoomMatchEndReason ending = null;
            MatchSnapshot publication = null;
            boolean accepted;

            synchronized (admission) {
                if (closed || !token().equals(command.token()) || !current((MatchStateCommand) command)) {
                    LOG.log(MatchLogEvent.COMMAND_REJECTED, matchId(snapshot.id().value()), reason("STALE_CONTEXT"));
                    return false;
                }

                if (!(command instanceof StartMatchCommand) && match.expired(clock.getAsLong())
                    && !(settings.executionMode() == MatchExecutionMode.CANNON_1V1
                        && (match.phase() == MatchPhase.ACTION || match.phase() == MatchPhase.SETTLING
                            || match.phase() == MatchPhase.COMPLETION))) {
                    ending = match.phase() != MatchPhase.LOADING && match.phase() != MatchPhase.SYNCHRONIZING
                        ? RoomMatchEndReason.PREVIEW_TIMEOUT : RoomMatchEndReason.LOADING_TIMEOUT;
                    warnings.warn(WarningEvent.LIMIT_EXCEEDED, ending.name());
                    accepted = false;
                } else {
                    accepted = apply((MatchStateCommand) command);
                }

                snapshot = MatchSnapshot.from(match);

                if (ending == null && callbacks != null && snapshot.phase() != MatchPhase.RESERVED
                    && publishedRevision != snapshot.phaseRevision()) {
                    publishedRevision = snapshot.phaseRevision();
                    publication = snapshot;
                }
            }

            if (ending != null) {
                terminate(ending);
            } else if (publication != null) {
                startDelivery = new MatchBootstrapDelivery(
                    publication,
                    recipients,
                    this,
                    scheduler,
                    progress(),
                    warnings
                ).dispatch(MatchBootstrapDelivery.requester(command, snapshot, true));
            }

            LOG.log(
                MatchLogEvent.COMMAND_PROCESSED,
                matchId(snapshot.id().value()),
                commandType(command.getClass().getSimpleName()),
                matchRevision(snapshot.phaseRevision()),
                durationMs(TimeUnit.NANOSECONDS.toMillis(clock.getAsLong() - started))
            );
            return accepted;
        } catch (RuntimeException failure) {
            LOG.failure(MatchLogEvent.COMMAND_FAILED, failure, matchId(snapshot.id().value()));
            terminate(RoomMatchEndReason.PREPARATION_FAILED);
            return false;
        }
    }

    private boolean current(MatchStateCommand command) {
        return switch (command) {
            case ConfirmMatchLoadingCommand c -> admits(c.context());
            case ConfirmMatchPositionCommand c -> admits(c.context());
            case ReportMatchEnemiesCommand c -> admits(c.context());
            case MatchDeliveryCompletedCommand c -> c.progress().equals(progress())
                && snapshot.participants().stream().anyMatch(p -> p.sessionId().equals(c.recipient()));
            case MatchDeadlineExpiredCommand c -> c.progress().equals(progress());
            default -> true;
        };
    }

    private boolean apply(MatchStateCommand command) {
        return switch (command) {
            case StartMatchCommand ignored -> match.commit(clock.getAsLong(), settings.loadingTimeoutNanos());
            case ConfirmMatchLoadingCommand c -> loaded(c);
            case ConfirmMatchPositionCommand c -> turns.position(c);
            case ReportMatchEnemiesCommand c -> report(c);
            case MatchDeliveryCompletedCommand c -> delivered(c);
            case MatchDeadlineExpiredCommand ignored -> false;
        };
    }

    RoomMatchTerminationResult termination() {
        return outcomes.current();
    }

    RoomMatchTerminationResult notifyTermination(RoomMatchEndReason reason, UUID requester) {
        return callbacks == null ? RoomMatchTerminationResult.ignored()
            : callbacks.terminateMatch(token(), reason, requester);
    }

    private boolean loaded(ConfirmMatchLoadingCommand command) {
        String result = match.loadingDone(command.context().sessionId(), command.slot(), command.x(), command.y());

        if (!result.equals("ACCEPTED") && !result.equals("UNCHANGED")) {
            warnings.warn(WarningEvent.FRAME_REJECTED, result);
            return false;
        }

        LOG.log(MatchLogEvent.LOADING_ACK, matchId(snapshot.id().value()), reason(result));
        beginSynchronization();
        return true;
    }

    private boolean beginSynchronization() {
        if (!match.beginSynchronization(clock.getAsLong())) {
            return false;
        }

        turns.firstTurnIssued();
        return true;
    }

    private boolean report(ReportMatchEnemiesCommand command) {
        if (match.phase() == MatchPhase.RESERVED) {
            return false;
        }

        long now = clock.getAsLong();
        var budget = reportBudgets.computeIfAbsent(
            command.context().sessionId(),
            ignored -> new MatchReportBudget(now)
        );

        if (!budget.accept(now, settings.reportsPerSecond())) {
            warnings.warn(WarningEvent.LIMIT_EXCEEDED, "MATCH_REPORT_RATE_EXCEEDED");
            return false;
        }

        return true;
    }

    private boolean delivered(MatchDeliveryCompletedCommand command) {
        if (!command.success()) {
            return true;
        }

        if (!match.deliveryCompleted(command.recipient())) {
            return false;
        }

        if (!beginSynchronization()) {
            boolean started = settings.executionMode() == MatchExecutionMode.PREVIEW
                ? match.previewStarted(clock.getAsLong(), settings.previewTimeoutNanos())
                : match.actionStarted(
                    clock.getAsLong(),
                    settings.executionMode() == MatchExecutionMode.CANNON_1V1
                    ? settings.actionTimeoutNanos() : settings.previewTimeoutNanos()
                );

            if (started) {
                LOG.log(MatchLogEvent.STARTED, matchId(snapshot.id().value()), reason(settings.executionMode().name()));
            }
        }

        return true;
    }

    CompletionStage<Boolean> deliveryCompleted(RoomMatchProgressToken progress, UUID recipient, boolean success) {
        return submit(new MatchDeliveryCompletedCommand(progress, recipient, success)).thenApply(
            handled -> {
                if (handled && !success) {
                    terminate(RoomMatchEndReason.START_DELIVERY_FAILED);
                }

                return handled;
            }
        );
    }

    void deadlineExpired(RoomMatchProgressToken progress) {
        if (settings.executionMode() == MatchExecutionMode.CANNON_1V1
            && (snapshot.phase() == MatchPhase.ACTION || snapshot.phase() == MatchPhase.SETTLING
                || snapshot.phase() == MatchPhase.COMPLETION)) {
            turns.enqueue(progress);
        } else {
            submit(new MatchDeadlineExpiredCommand(progress));
        }
    }

    boolean beginPublication(MatchSnapshot publication) {
        synchronized (admission) {
            if (closed) {
                return false;
            }

            if (publication.phase() == MatchPhase.LOADING) {
                startPublished = true;
            }

            return true;
        }
    }

    CompletionStage<Void> delivery() {
        return startDelivery;
    }

    MatchSnapshot stop() {
        synchronized (admission) {
            if (closed) {
                return null;
            }

            closed = true;
            return snapshot;
        }
    }

    boolean unpublished() {
        synchronized (admission) {
            return callbacks != null && !startPublished;
        }
    }

    CompletionStage<Void> finish(Runnable delivery) {
        return mailbox.terminate(
            () -> {
                scheduler.cancel(snapshot.id().value());
                match.beginTermination();
                match.cancel();
                items.clear();
                delivery.run();
                return true;
            }
        ).thenApply(ignored -> null);
    }

    private void rejected(MatchCommand command) {
        warnings.warn(WarningEvent.LIMIT_EXCEEDED, "MATCH_COMMAND_QUEUE_FULL");
        UUID sender = MatchBootstrapDelivery.requester(command, snapshot, false);
        recipients.stream().filter(p -> p.sessionId().equals(sender))
            .forEach(p -> p.output().close("MATCH_COMMAND_QUEUE_FULL"));
        terminate(RoomMatchEndReason.PREPARATION_FAILED);
    }

    void terminate(RoomMatchEndReason reason) {
        outcomes.cancel(reason, null);
    }
}
