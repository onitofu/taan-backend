package taan.online.match.service;

import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.fromPhase;
import static taan.online.logging.LogField.lifecycleEvent;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.phaseRevision;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomGeneration;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.toPhase;
import static taan.online.logging.LogField.turnRevision;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import taan.online.logging.EventLogger;
import taan.online.logging.WarningEvent;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.Match;
import taan.online.match.model.MatchExitKind;
import taan.online.match.model.MatchLifecycleEvent;
import taan.online.match.model.MatchParticipationState;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.MatchTransitionDecision;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.CommittedScenario;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.match.service.model.MatchCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchDeadlineExpiredCommand;
import taan.online.match.service.model.MatchDeliveryCompletedCommand;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.MatchTurnToken;
import taan.online.match.service.model.PreparedScenario;
import taan.online.match.service.model.ScenarioApplicationResult;
import taan.online.match.service.model.TerminateMatchCommand;
import taan.online.room.service.RoomMatchCallbacks;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchProgressToken;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchToken;

final class MatchRuntime {
    private static final EventLogger LOG = EventLogger.forClass(MatchRuntime.class);

    private final Match match;
    final MatchLifecycleCoordinator lifecycle;
    final MatchScenarioExecution scenarios;
    final MatchMovementExecution movement;
    final MatchItemExecution items;
    final MatchOutcomeExecution outcomes;
    private final MatchMailbox mailbox;
    private final MatchAdmissionAccess access;
    private final MatchBootstrapSettings settings;
    private final MatchScheduler scheduler;
    private final LongSupplier clock;
    private final WarningLogLimiter warnings;
    private final Object admission = new Object();
    private volatile MatchSnapshot snapshot;
    private volatile boolean closed;
    private MatchParticipationState stoppedParticipation;
    volatile long publishedRevision = -1;
    private boolean startPublished;
    RoomMatchCallbacks callbacks;
    private List<MatchSubscription> recipients = List.of();
    volatile CompletionStage<Void> startDelivery = CompletableFuture.completedFuture(null);

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
        lifecycle = new MatchLifecycleCoordinator(this, match, admission, settings, scheduler, clock, warnings);
        scenarios = new MatchScenarioExecution(this, access, settings.lootPolicy());
        outcomes = new MatchOutcomeExecution(this, match, admission, clock);
        items = new MatchItemExecution(this, match, admission, clock, settings.lootPolicy(), warnings);
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

    Optional<MatchSnapshot> commitScenario(ApplyScenarioCommand command, PreparedScenario prepared) {
        synchronized (admission) {
            if (!scenarioCurrent(command) || match.expired(clock.getAsLong())
                || !match.commitScenario(
                    prepared.baseWorldRevision(),
                    command.turnRevision(),
                    prepared.world(),
                    lifecycle.completion(prepared),
                    prepared.baseLoot(),
                    prepared.loot()
                )) {
                return Optional.empty();
            }

            match.updateParticipation(match.participation().finish(clock.getAsLong(), false));
            items.clear();
            refresh();
            return Optional.of(snapshot);
        }
    }

    CompletionStage<Void> publishScenario(CommittedScenario scenario, ScenarioApplicationResult result) {
        if (settings.executionMode() == MatchExecutionMode.CLIENT_SCENARIO) {
            lifecycle.arm();
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
        return settings.executionMode() == MatchExecutionMode.CLIENT_SCENARIO;
    }

    boolean reportCurrent(MatchCommandContext context) {
        return live() && admits(context) && !match.resolution().exits().containsKey(context.sessionId())
            && (match.phase() == MatchPhase.ACTION || match.phase() == MatchPhase.SETTLING
                || match.phase() == MatchPhase.COMPLETION);
    }

    CompletableFuture<Boolean> confirmScenario(ConfirmScenarioCommand command) {
        return turnWork(
            () -> settings.executionMode() == MatchExecutionMode.CLIENT_SCENARIO && lifecycle.acknowledge(command)
        );
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
        turnWork(() -> lifecycle.expired(token, fingerprint));
    }

    List<MatchSubscription> recipients() {
        return recipients;
    }

    void refresh() {
        bootstrapRefresh();
        publishedRevision = snapshot.phaseRevision();
    }

    void bootstrapRefresh() {
        var before = snapshot;
        snapshot = MatchSnapshot.from(match);
        if (before.phaseRevision() != snapshot.phaseRevision()) {
            LOG.log(
                MatchLogEvent.LIFECYCLE_TRANSITION,
                lifecycleEvent(match.lastLifecycleEvent().name()),
                matchId(snapshot.id().value()),
                roomId(snapshot.roomId().value()),
                roomGeneration(snapshot.roomGeneration()),
                channelId(snapshot.channelId()),
                fromPhase(before.phase().name()),
                toPhase(snapshot.phase().name()),
                phaseRevision(snapshot.phaseRevision()),
                turnRevision(snapshot.turn().revision())
            );
        }

        MatchLootExecution.logQuizChanges(before, snapshot);
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

    MatchParticipationState participation() {
        synchronized (admission) {
            return closed ? stoppedParticipation : match.participation();
        }
    }

    RoomMatchToken token() {
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
        return !closed && snapshot.resolution().exits().get(context.sessionId()) != MatchExitKind.DEPARTED
            && token().equals(context.token()) && snapshot.channelId() == context.channelId()
            && snapshot.participants().stream().anyMatch(
                p -> p.sessionId().equals(context.sessionId()) && p.admissionId().equals(context.admissionId())
                    && access.current(context, p)
            );
    }

    CompletableFuture<Boolean> submit(MatchCommand command) {
        return mailbox.submit(() -> lifecycle.execute(command), () -> rejected(command));
    }

    RoomMatchTerminationResult termination() {
        return outcomes.current();
    }

    RoomMatchTerminationResult notifyTermination(RoomMatchEndReason reason, UUID requester) {
        return callbacks == null ? RoomMatchTerminationResult.ignored()
            : callbacks.terminateMatch(token(), reason, requester);
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
        if (live() && snapshot.phase() == MatchPhase.LOADING) {
            turnWork(() -> lifecycle.loadingExpired(progress));
        } else if (settings.executionMode() == MatchExecutionMode.CLIENT_SCENARIO
            && (snapshot.phase() == MatchPhase.ACTION || snapshot.phase() == MatchPhase.SETTLING
                || snapshot.phase() == MatchPhase.COMPLETION)) {
            lifecycle.enqueue(progress);
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
        MatchSnapshot stopped;

        synchronized (admission) {
            if (closed) {
                return null;
            }

            closed = true;
            stoppedParticipation = match.participation().stop(clock.getAsLong());
            stopped = snapshot;
        }

        mailbox.fence();
        return stopped;
    }

    boolean unpublished() {
        synchronized (admission) {
            return callbacks != null && !startPublished;
        }
    }

    void control(Runnable command, Runnable rejected) {
        mailbox.control(
            () -> {
                try {
                    command.run();
                    return true;
                } catch (RuntimeException failure) {
                    LOG.failure(MatchLogEvent.COMMAND_FAILED, failure, matchId(snapshot.id().value()));
                    rejected.run();
                    return false;
                }
            },
            false,
            rejected
        );
    }

    void beginTermination() {
        scheduler.cancel(snapshot.id().value());
        match.beginTermination();
        items.clear();
        refresh();
    }

    void transition(MatchLifecycleEvent event) {
        var result = match.transition(event);

        if (result.decision() != MatchTransitionDecision.APPLIED) {
            ignoredLifecycle(event.name(), result.decision().name());
        }

        refresh();
    }

    void ignoredLifecycle(String event, String rejection) {
        LOG.log(
            MatchLogEvent.LIFECYCLE_IGNORED,
            matchId(snapshot.id().value()),
            roomId(snapshot.roomId().value()),
            roomGeneration(snapshot.roomGeneration()),
            channelId(snapshot.channelId()),
            lifecycleEvent(event),
            phaseRevision(snapshot.phaseRevision()),
            turnRevision(snapshot.turn().revision()),
            reason(rejection)
        );
    }

    CompletionStage<Void> finish(Runnable delivery) {
        return mailbox.terminate(
            () -> {
                scheduler.cancel(snapshot.id().value());
                match.cancel();
                refresh();
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
