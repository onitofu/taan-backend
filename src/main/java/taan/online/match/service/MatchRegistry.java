package taan.online.match.service;

import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomGeneration;
import static taan.online.logging.LogField.roomId;
import static taan.online.match.service.MatchReservationLog.log;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import taan.online.logging.EventLogger;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.Match;
import taan.online.match.model.MatchDefinitionCatalog;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.MatchTurnPolicy;
import taan.online.match.service.model.ActivateSpecialCommand;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ConfirmMatchLoadingCommand;
import taan.online.match.service.model.ConfirmMatchPositionCommand;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.DepartMatchCommand;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.match.service.model.MatchCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchRequestBinding;
import taan.online.match.service.model.MatchResultReceipt;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.MoveMatchActorCommand;
import taan.online.match.service.model.PassMatchTurnCommand;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.match.service.model.ReportMatchEnemiesCommand;
import taan.online.match.service.model.ScenarioApplicationResult;
import taan.online.match.service.model.StartMatchCommand;
import taan.online.match.service.model.TerminateMatchCommand;
import taan.online.room.model.RoomMode;
import taan.online.room.service.RoomMatchCallbacks;
import taan.online.room.service.RoomMatchLifecycle;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchDraft;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchProgressToken;
import taan.online.room.service.model.RoomMatchReservationOutcome;
import taan.online.room.service.model.RoomMatchReservationResult;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchTerminationMode;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;

public final class MatchRegistry implements RoomMatchLifecycle {
    private static final EventLogger LOG = EventLogger.forClass(MatchRegistry.class);

    private final MatchDefinitionCatalog definitions;
    private final MatchPreparation preparation;
    private final MatchBootstrapSettings settings;
    private final MatchScheduler scheduler;
    private final LongSupplier clock;
    private final RandomGenerator random;
    private final WarningLogLimiter warnings;
    private final MatchCommandExecutor executor;
    private final int capacity;
    private final MatchAdmissionAccess access;
    private final Map<UUID, MatchRuntime> sessions = new ConcurrentHashMap<>();
    private final Map<MatchId, MatchRuntime> matches = new ConcurrentHashMap<>();
    private final Map<UUID, MatchSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<UUID, MatchResultReceipt> receipts = new ConcurrentHashMap<>();
    private final Map<RoomMatchToken, MatchTerminationDispatch> terminations = new ConcurrentHashMap<>();

    public MatchRegistry(
        MatchDefinitionCatalog definitions,
        MatchBootstrapSettings settings,
        MatchScheduler scheduler,
        LongSupplier clock,
        RandomGenerator random,
        WarningLogLimiter warnings,
        MatchCommandExecutor executor,
        int capacity,
        MatchAdmissionAccess access
    ) {
        this.definitions = Objects.requireNonNull(definitions);
        preparation = new MatchPreparation(definitions);
        this.settings = settings;
        this.scheduler = scheduler;
        this.clock = clock;
        this.random = random;
        this.warnings = warnings;
        this.executor = executor;
        this.capacity = capacity;
        this.access = access;
    }

    @Override
    public synchronized RoomMatchReservationResult reserve(RoomMatchDraft draft) {
        var token = draft.token();
        var existing = current(token);

        if (existing.isPresent()) {
            return result(RoomMatchReservationOutcome.UNCHANGED, token, "ALREADY_RESERVED");
        }

        String rejection = preparation.rejection(
            draft,
            matches.values().stream().map(value -> value.snapshot()).toList()
        );

        if (rejection != null) {
            MatchReservationLog.log(MatchLogEvent.RESERVATION_REJECTED, draft, rejection);
            return result(RoomMatchReservationOutcome.REJECTED, null, rejection);
        }

        try {
            var participants = preparation.participants(draft);
            var match = new Match(
                new MatchId(token.matchId()),
                token.roomId(),
                token.generation(),
                draft.channelId(),
                draft.ownerSessionId(),
                draft.settings().mode(),
                draft.settings().rule(),
                definitions.select(draft.settings().mapId()),
                participants,
                draft.captains(),
                participants.isEmpty() ? 0 : participants.get(random.nextInt(participants.size())).slotIndex()
            );
            var profile = settings.forProfile(draft.settings().mode() == RoomMode.ITEM && participants.size() == 2);

            if (profile.executionMode() == MatchExecutionMode.CANNON_1V1) {
                var initial = new MatchTurnPolicy().initial(participants, random.nextLong());
                match = new Match(
                    match.id(),
                    match.roomId(),
                    match.roomGeneration(),
                    match.channelId(),
                    match.ownerSessionId(),
                    match.mode(),
                    match.rule(),
                    match.definitions(),
                    participants,
                    match.captains(),
                    initial
                );
            }

            var runtime = new MatchRuntime(match, profile, scheduler, clock, warnings, executor, capacity, access);
            matches.put(match.id(), runtime);
            participants.forEach(value -> sessions.put(value.sessionId(), runtime));
            participants.forEach(value -> receipts.remove(value.sessionId()));
        } catch (IllegalArgumentException failure) {
            MatchReservationLog.log(MatchLogEvent.RESERVATION_REJECTED, draft, "INVALID_SETUP");
            return result(RoomMatchReservationOutcome.REJECTED, null, "INVALID_SETUP");
        } catch (RuntimeException failure) {
            LOG.failure(
                MatchLogEvent.RESERVATION_FAILED,
                failure,
                matchId(token.matchId()),
                roomId(token.roomId().value()),
                roomGeneration(token.generation())
            );
            throw failure;
        }

        MatchReservationLog.log(MatchLogEvent.RESERVED, draft, "RESERVED");
        return result(RoomMatchReservationOutcome.RESERVED, token, "RESERVED");
    }

    @Override
    public CompletionStage<RoomMatchReservationResult> commit(RoomMatchToken token) {
        var runtime = find(token);

        if (runtime == null) {
            return CompletableFuture.completedFuture(result(RoomMatchReservationOutcome.STALE, null, "STALE_MATCH"));
        }

        return runtime.submit(new StartMatchCommand(token)).thenApply(
            accepted -> result(
                accepted ? RoomMatchReservationOutcome.COMMITTED : RoomMatchReservationOutcome.UNCHANGED,
                token,
                accepted ? "LOADING" : "ALREADY_COMMITTED"
            )
        );
    }

    @Override
    public RoomMatchReservationResult cancel(RoomMatchToken token, String closeReason) {
        var runtime = find(token);

        if (runtime == null) {
            var pending = terminations.remove(token);

            if (pending != null) {
                pending.runtime().finish(() -> { });
            }

            receipts.values().removeIf(value -> value.token().equals(token));
            return result(RoomMatchReservationOutcome.STALE, null, "STALE_MATCH");
        }

        var snapshot = runtime.stop();

        if (snapshot == null) {
            return result(RoomMatchReservationOutcome.STALE, null, "STALE_MATCH");
        }

        matches.remove(snapshot.id(), runtime);
        snapshot.participants().forEach(value -> sessions.remove(value.sessionId(), runtime));
        runtime.finish(() -> { });
        log(
            snapshot.phase() == MatchPhase.RESERVED ? MatchLogEvent.RESERVATION_ROLLED_BACK : MatchLogEvent.CANCELLED,
            token,
            closeReason
        );
        return result(RoomMatchReservationOutcome.ROLLED_BACK, token, closeReason);
    }

    public void subscribe(UUID id, MatchOutput output) {
        subscriptions.putIfAbsent(id, new MatchSubscription(id, output));
    }

    public void unsubscribe(UUID id) {
        if (id == null) {
            return;
        }

        subscriptions.remove(id);
        receipts.remove(id);
    }

    @Override
    public boolean activate(RoomMatchToken token, RoomMatchCallbacks callbacks) {
        var runtime = find(token);
        return runtime != null && runtime.activate(
            Objects.requireNonNull(callbacks),
            runtime.snapshot().participants().stream().map(p -> subscriptions.get(p.sessionId()))
                .filter(Objects::nonNull).toList()
        );
    }

    public RoomMatchProgressToken progress(RoomMatchToken token) {
        var runtime = find(token);
        return runtime == null ? null : runtime.progress();
    }

    @Override
    public CompletionStage<Void> publish(RoomMatchToken token) {
        var runtime = find(token);
        return runtime == null ? CompletableFuture.completedFuture(null) : runtime.delivery();
    }

    public MatchCommandContext context(UUID session) {
        var runtime = sessions.get(session);

        if (runtime != null) {
            var snapshot = runtime.snapshot();
            var context = MatchCommandContext.from(snapshot, session);
            return runtime.admits(context) ? context : null;
        }

        var receipt = receipts.get(session);
        return receipt == null ? null : MatchCommandContext.from(
            new RoomMatchContext(receipt.membership(), receipt.token())
        );
    }

    public CompletionStage<RoomMatchTerminationResult> terminate(RoomMatchContext context, RoomMatchEndReason reason) {
        var runtime = find(context.token());
        return runtime == null ? CompletableFuture.completedFuture(RoomMatchTerminationResult.ignored())
            : runtime.submit(new TerminateMatchCommand(MatchCommandContext.from(context), reason)).thenCompose(
                accepted -> accepted ? runtime.outcomes.result()
                    : CompletableFuture.completedFuture(RoomMatchTerminationResult.ignored())
            );
    }

    public boolean scenarioEnabled(RoomMatchContext context) {
        var runtime = context == null ? null : find(context.token());
        return runtime != null && runtime.live();
    }

    public CompletionStage<Boolean> activateSpecial(ActivateSpecialCommand command) {
        var runtime = find(command.context().token());
        return runtime == null ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.items.activate(command));
    }

    public CompletionStage<Boolean> recordSpend(RecordMatchSpendCommand command) {
        var runtime = find(command.context().token());
        return runtime == null ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.items.record(command));
    }

    public CompletionStage<Boolean> move(MoveMatchActorCommand command) {
        var runtime = find(command.context().token());
        return runtime == null ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.movement.move(command));
    }

    public CompletionStage<Boolean> passTurn(PassMatchTurnCommand command) {
        var runtime = find(command.context().token());
        return runtime == null ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.movement.pass(command));
    }

    public CompletionStage<Boolean> confirmScenario(ConfirmScenarioCommand command) {
        var runtime = find(command.context().token());
        return runtime == null ? CompletableFuture.completedFuture(false) : runtime.confirmScenario(command);
    }

    public MatchRequestBinding capture(UUID session) {
        var context = context(session);
        var runtime = sessions.get(session);
        var snapshot = runtime == null ? Optional.<MatchSnapshot>empty() : Optional.of(runtime.snapshot());
        return MatchRequestBinding.from(context, snapshot);
    }

    public CompletionStage<ScenarioApplicationResult> applyScenario(ApplyScenarioCommand command) {
        var runtime = find(command.context().token());
        return runtime == null ? CompletableFuture.completedFuture(ScenarioApplicationResult.ignored("STALE_MATCH"))
            : runtime.applyScenario(command);
    }

    public CompletionStage<Boolean> submit(MatchCommand command) {
        var runtime = find(command.token());
        return runtime == null ? CompletableFuture.completedFuture(false) : runtime.submit(command);
    }

    public CompletionStage<RoomMatchProgressToken> loadingDone(RoomMatchContext context, int slot, int x, int y) {
        if (context == null) {
            return CompletableFuture.completedFuture(null);
        }

        var runtime = find(context.token());
        var previous = progress(context.token());
        return submit(new ConfirmMatchLoadingCommand(MatchCommandContext.from(context), slot, x, y)).thenApply(
            accepted -> accepted && !Objects.equals(previous, runtime.progress())
                ? runtime.progress() : null
        );
    }

    public CompletionStage<RoomMatchProgressToken> deliveryCompleted(RoomMatchProgressToken token, UUID recipient) {
        var runtime = find(token.match());
        return runtime == null ? CompletableFuture.completedFuture(null)
            : runtime.deliveryCompleted(token, recipient, true).thenApply(
                accepted -> accepted && !Objects.equals(token, runtime.progress()) ? runtime.progress() : null
            );
    }

    public CompletionStage<Boolean> confirmPosition(RoomMatchContext context, int slot, int x, int y, int facing) {
        return context == null ? CompletableFuture.completedFuture(false)
            : submit(new ConfirmMatchPositionCommand(MatchCommandContext.from(context), slot, x, y, facing));
    }

    public CompletionStage<Boolean> reportEnemies(RoomMatchContext context) {
        return context == null ? CompletableFuture.completedFuture(false)
            : submit(new ReportMatchEnemiesCommand(MatchCommandContext.from(context)));
    }

    public CompletionStage<ScenarioApplicationResult> rejectMalformedScenario(
        MatchCommandContext context,
        long turn,
        long world,
        long phase
    ) {
        return MatchScenarioExecution.rejectMalformed(find(context.token()), context, turn, world, phase);
    }

    @Override
    public CompletionStage<Boolean> requestDeparture(
        RoomMatchToken token,
        RoomMembership expected,
        RoomMatchEndReason reason
    ) {
        var runtime = find(token);
        var command = new DepartMatchCommand(MatchCommandContext.from(new RoomMatchContext(expected, token)), reason);
        return runtime == null || !runtime.outcomes.acceptsDeparture() ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.outcomes.depart(command));
    }

    @Override
    public RoomMatchTermination prepareTermination(
        RoomMatchToken token,
        RoomMatchEndReason closeReason,
        UUID departing
    ) {
        var runtime = find(token);
        var snapshot = runtime == null ? null : runtime.stop();

        if (snapshot == null) {
            log(MatchLogEvent.TERMINATION_IGNORED, token, "STALE_MATCH");
            return null;
        }

        var mode = MatchTerminationDelivery.mode(
            snapshot.phase(),
            closeReason,
            snapshot.phase() == MatchPhase.LOADING && runtime.unpublished()
        );
        var recipients = MatchTerminationDelivery.memberships(snapshot, mode, departing);
        var terminal = new RoomMatchTermination(token, closeReason, mode, recipients);
        var outputs = mode == RoomMatchTerminationMode.ROLLBACK ? List.<MatchSubscription>of() : recipients.stream()
            .map(value -> subscriptions.get(value.sessionId())).filter(Objects::nonNull).toList();
        var exiting = mode == RoomMatchTerminationMode.RETURN_TO_ROOM && closeReason == RoomMatchEndReason.ROOM_LEAVE
            ? departing == null ? null : subscriptions.get(departing) : null;
        var outcome = MatchOutcomeExecution.terminal(snapshot, runtime.live(), closeReason);
        MatchOutcomeExecution.finished(snapshot, outcome, closeReason);
        terminations.put(
            token,
            new MatchTerminationDispatch(
                runtime,
                new MatchTerminationDelivery(terminal, outputs, exiting, outcome)
            )
        );

        if (mode == RoomMatchTerminationMode.RETURN_TO_ROOM) {
            recipients.forEach(value -> receipts.put(value.sessionId(), new MatchResultReceipt(token, value)));
        }

        matches.remove(snapshot.id(), runtime);
        snapshot.participants().forEach(value -> sessions.remove(value.sessionId(), runtime));
        return terminal;
    }

    @Override
    public CompletionStage<Void> dispatchTermination(RoomMatchTermination terminal, UUID requester, Runnable queued) {
        var claimed = new AtomicReference<MatchTerminationDispatch>();
        terminations.computeIfPresent(
            terminal.token(),
            (token, pending) -> {
                if (!pending.delivery().terminal().equals(terminal)) {
                    return pending;
                }

                claimed.set(pending);
                return null;
            }
        );
        var pending = claimed.get();

        if (pending == null) {
            return CompletableFuture.completedFuture(null);
        }

        return pending.dispatch(requester, queued);
    }

    @Override
    public RoomMatchToken resultToken(RoomMembership membership) {
        var receipt = receipts.get(membership.sessionId());
        return receipt != null && receipt.membership().equals(membership) ? receipt.token() : null;
    }

    public boolean consumeResult(RoomMatchContext context) {
        return context.token().equals(resultToken(context.membership()));
    }

    @Override
    public void release(RoomMembership membership) {
        receipts.computeIfPresent(
            membership.sessionId(),
            (id, value) -> value.membership().equals(membership) ? null : value
        );
    }

    public Optional<MatchSnapshot> current(RoomMatchToken token) {
        var runtime = find(token);
        return runtime == null ? Optional.empty() : Optional.of(runtime.snapshot());
    }

    private MatchRuntime find(RoomMatchToken token) {
        var runtime = matches.get(new MatchId(token.matchId()));
        var snapshot = runtime == null ? null : runtime.snapshot();
        return snapshot != null && snapshot.roomId().equals(token.roomId())
            && snapshot.roomGeneration().equals(token.generation()) ? runtime : null;
    }

    private static RoomMatchReservationResult result(
        RoomMatchReservationOutcome outcome,
        RoomMatchToken token,
        String reason
    ) {
        return new RoomMatchReservationResult(outcome, reason, token);
    }
}
