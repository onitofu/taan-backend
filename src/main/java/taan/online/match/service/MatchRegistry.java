package taan.online.match.service;

import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.mapId;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomGeneration;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.roomMode;
import static taan.online.logging.LogField.sessionId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import taan.online.logging.WarningLogLimiter;
import taan.online.logging.WarningEvent;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.room.service.RoomMatchCallbacks;
import taan.online.room.service.model.RoomMatchProgressToken;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.Objects;
import java.util.Optional;
import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.Match;
import taan.online.match.model.MatchDefinitions;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.MatchResultReceipt;
import taan.online.room.service.RoomMatchLifecycle;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchDraft;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchTerminationMode;
import taan.online.room.service.model.RoomMembership;
import taan.online.room.service.model.RoomMatchReservationOutcome;
import taan.online.room.service.model.RoomMatchReservationResult;
import taan.online.room.service.model.RoomMatchToken;

public final class MatchRegistry implements RoomMatchLifecycle {
    private static final EventLogger LOG = EventLogger.forClass(MatchRegistry.class);

    private final MatchDefinitions definitions;
    private final MatchPreparation preparation;
    private final MatchBootstrapSettings settings;
    private final MatchScheduler scheduler;
    private final LongSupplier clock;
    private final RandomGenerator random;
    private final WarningLogLimiter warnings;
    private final Map<MatchId, MatchRuntime> matches = new HashMap<>();
    private final Map<UUID, MatchSubscription> subscriptions = new HashMap<>();
    private final Map<UUID, MatchResultReceipt> receipts = new HashMap<>();
    private final Map<RoomMatchToken, MatchTerminationDelivery> terminations = new HashMap<>();

    public MatchRegistry(
        MatchDefinitions definitions,
        MatchBootstrapSettings settings,
        MatchScheduler scheduler,
        LongSupplier clock,
        RandomGenerator random,
        WarningLogLimiter warnings
    ) {
        this.definitions = Objects.requireNonNull(definitions);
        preparation = new MatchPreparation(definitions);
        this.settings = settings;
        this.scheduler = scheduler;
        this.clock = clock;
        this.random = random;
        this.warnings = warnings;
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
            matches.values().stream().map(value -> MatchSnapshot.from(value.match)).toList()
        );

        if (rejection != null) {
            log(MatchLogEvent.RESERVATION_REJECTED, draft, rejection);
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
                definitions,
                participants,
                draft.captains(),
                participants.isEmpty() ? 0 : participants.get(random.nextInt(participants.size())).slotIndex()
            );
            matches.put(match.id(), new MatchRuntime(match));
            participants.forEach(value -> receipts.remove(value.sessionId()));
        } catch (IllegalArgumentException failure) {
            log(MatchLogEvent.RESERVATION_REJECTED, draft, "INVALID_SETUP");
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

        log(MatchLogEvent.RESERVED, draft, "RESERVED");
        return result(RoomMatchReservationOutcome.RESERVED, token, "RESERVED");
    }

    @Override
    public synchronized RoomMatchReservationResult commit(RoomMatchToken token) {
        var match = find(token);

        if (match == null) {
            return result(RoomMatchReservationOutcome.STALE, null, "STALE_MATCH");
        }

        if (!match.commit(clock.getAsLong(), settings.loadingTimeoutNanos())) {
            return result(RoomMatchReservationOutcome.UNCHANGED, token, "ALREADY_COMMITTED");
        }

        log(MatchLogEvent.LOADING, token, "LOADING");
        return result(RoomMatchReservationOutcome.COMMITTED, token, "LOADING");
    }

    @Override
    public synchronized RoomMatchReservationResult cancel(RoomMatchToken token, String closeReason) {
        var match = find(token);

        if (match == null) {
            terminations.remove(token);
            receipts.values().removeIf(value -> value.token().equals(token));
            return result(RoomMatchReservationOutcome.STALE, null, "STALE_MATCH");
        }

        var previous = match.phase();
        match.cancel();
        matches.remove(match.id());
        scheduler.cancel(match.id().value());
        log(
            previous == MatchPhase.RESERVED ? MatchLogEvent.RESERVATION_ROLLED_BACK : MatchLogEvent.CANCELLED,
            token,
            closeReason
        );
        return result(RoomMatchReservationOutcome.ROLLED_BACK, token, closeReason);
    }

    public synchronized void subscribe(UUID sessionId, MatchOutput output) {
        subscriptions.putIfAbsent(sessionId, new MatchSubscription(sessionId, output));
    }

    public synchronized void unsubscribe(UUID sessionId) {
        subscriptions.remove(sessionId);
        receipts.remove(sessionId);
    }

    @Override
    public synchronized boolean activate(RoomMatchToken token, RoomMatchCallbacks callbacks) {
        var match = find(token);

        if (match == null || match.phase() != MatchPhase.RESERVED) {
            return false;
        }

        var runtime = matches.get(match.id());
        var recipients = match.participants().stream().map(value -> subscriptions.get(value.sessionId()))
            .filter(Objects::nonNull).toList();

        if (recipients.size() != match.participants().size()) {
            return false;
        }

        runtime.callbacks = Objects.requireNonNull(callbacks);
        runtime.recipients = recipients;
        return true;
    }

    @Override
    public synchronized RoomMatchProgressToken progress(RoomMatchToken token) {
        var match = find(token);
        return match == null ? null : progress(match);
    }

    @Override
    public CompletionStage<Void> publish(RoomMatchProgressToken expected, UUID requester) {
        MatchBootstrapDelivery delivery;
        MatchRuntime runtime;

        synchronized (this) {
            var match = currentProgress(expected);

            if (match == null || match.phase() == MatchPhase.RESERVED) {
                return CompletableFuture.completedFuture(null);
            }

            runtime = matches.get(match.id());

            if (runtime.callbacks == null || runtime.publishedRevision == expected.revision()) {
                return CompletableFuture.completedFuture(null);
            }

            runtime.publishedRevision = expected.revision();
            delivery = new MatchBootstrapDelivery(
                MatchSnapshot.from(match),
                runtime.recipients,
                runtime.callbacks,
                scheduler,
                expected,
                warnings
            );
        }

        try {
            return delivery.dispatch(requester);
        } catch (RuntimeException failure) {
            synchronized (this) {
                runtime.publishedRevision = -1;
            }

            throw failure;
        }
    }

    @Override
    public synchronized RoomMatchProgressToken loadingDone(RoomMatchContext context, int slot, int x, int y) {
        var runtime = actor(context);

        if (runtime == null) {
            return null;
        }

        String outcome = runtime.match.loadingDone(context.membership().sessionId(), slot, x, y);

        if (!outcome.equals("ACCEPTED") && !outcome.equals("UNCHANGED")) {
            warnings.warn(WarningEvent.FRAME_REJECTED, outcome);
            return null;
        }

        log(MatchLogEvent.LOADING_ACK, context.token(), outcome);
        return runtime.match.beginSynchronization() ? progress(runtime.match) : null;
    }

    @Override
    public synchronized RoomMatchProgressToken deliveryCompleted(RoomMatchProgressToken token, UUID recipient) {
        var match = currentProgress(token);

        if (match == null || !match.deliveryCompleted(recipient)) {
            return null;
        }

        if (match.beginSynchronization()) {
            return progress(match);
        }

        if (match.previewStarted(clock.getAsLong(), settings.previewTimeoutNanos())) {
            log(MatchLogEvent.STARTED, token.match(), "PREVIEW");
            return progress(match);
        }

        return null;
    }

    @Override
    public synchronized boolean confirmPosition(RoomMatchContext context, int slot, int x, int y, int facing) {
        var runtime = actor(context);
        boolean valid = runtime != null
            && runtime.match.confirmsPosition(context.membership().sessionId(), slot, x, y, facing);

        if (valid) {
            log(MatchLogEvent.POSITION_CONFIRMED, context.token(), "UNCHANGED");
        } else {
            warnings.warn(WarningEvent.FRAME_REJECTED, "INVALID_POSITION_CONFIRMATION");
        }

        return valid;
    }

    @Override
    public synchronized boolean reportEnemies(RoomMatchContext context) {
        var runtime = actor(context);

        if (runtime == null || runtime.match.phase() == MatchPhase.RESERVED) {
            return false;
        }

        long now = clock.getAsLong();
        var budget = runtime.reportBudgets.computeIfAbsent(
            context.membership().sessionId(),
            ignored -> new MatchReportBudget(now)
        );

        if (!budget.accept(now, settings.reportsPerSecond())) {
            warnings.warn(WarningEvent.LIMIT_EXCEEDED, "MATCH_REPORT_RATE_EXCEEDED");
            return false;
        }

        return true;
    }

    @Override
    public synchronized RoomMatchEndReason timeoutReason(RoomMatchProgressToken token) {
        var match = currentProgress(token);

        if (match == null || !match.expired(clock.getAsLong())) {
            return null;
        }

        var reason = match.phase() == MatchPhase.PREVIEW
            ? RoomMatchEndReason.PREVIEW_TIMEOUT : RoomMatchEndReason.LOADING_TIMEOUT;
        warnings.warn(WarningEvent.LIMIT_EXCEEDED, reason.name());
        return reason;
    }

    private MatchRuntime actor(RoomMatchContext context) {
        var match = find(context.token());

        if (match == null || match.channelId() != context.membership().channelId()) {
            return null;
        }

        boolean admitted = match.participants().stream().anyMatch(
            value ->
                value.sessionId().equals(context.membership().sessionId())
                    && value.admissionId().equals(context.membership().admissionId())
        );
        return admitted ? matches.get(match.id()) : null;
    }

    private Match currentProgress(RoomMatchProgressToken token) {
        var match = find(token.match());
        return match != null && match.phaseRevision() == token.revision() ? match : null;
    }

    private static RoomMatchProgressToken progress(Match match) {
        return new RoomMatchProgressToken(
            new RoomMatchToken(match.roomId(), match.roomGeneration(), match.id().value()),
            match.phaseRevision()
        );
    }

    @Override
    public synchronized RoomMatchTermination prepareTermination(
        RoomMatchToken token,
        RoomMatchEndReason closeReason,
        UUID departing
    ) {
        var match = find(token);

        if (match == null) {
            log(MatchLogEvent.TERMINATION_IGNORED, token, "STALE_MATCH");
            return null;
        }

        var previous = match.phase();
        var runtime = matches.get(match.id());
        var mode = MatchTerminationDelivery.mode(
            previous,
            closeReason,
            previous == MatchPhase.LOADING && runtime.callbacks != null && runtime.publishedRevision < 0
        );
        match.beginTermination();
        var recipients = match.participants().stream()
            .filter(value -> mode != RoomMatchTerminationMode.RETURN_TO_ROOM || !value.sessionId().equals(departing))
            .map(
                value -> new RoomMembership(
                    value.sessionId(),
                    match.roomId(),
                    match.roomGeneration(),
                    match.channelId(),
                    value.admissionId()
                )
            ).toList();
        var terminal = new RoomMatchTermination(token, closeReason, mode, recipients);
        var outputs = mode == RoomMatchTerminationMode.ROLLBACK ? List.<MatchSubscription>of() : recipients.stream()
            .map(value -> subscriptions.get(value.sessionId())).filter(Objects::nonNull).toList();
        var exiting = mode == RoomMatchTerminationMode.RETURN_TO_ROOM && closeReason == RoomMatchEndReason.ROOM_LEAVE
            ? subscriptions.get(departing) : null;
        terminations.put(token, new MatchTerminationDelivery(terminal, outputs, exiting));

        if (mode == RoomMatchTerminationMode.RETURN_TO_ROOM) {
            recipients.forEach(value -> receipts.put(value.sessionId(), new MatchResultReceipt(token, value)));
        }

        matches.remove(match.id());
        scheduler.cancel(match.id().value());
        match.cancel();
        log(MatchLogEvent.CANCELLED, token, closeReason.name());
        return terminal;
    }

    @Override
    public CompletionStage<Void> dispatchTermination(RoomMatchTermination terminal, UUID requester) {
        MatchTerminationDelivery delivery;

        synchronized (this) {
            delivery = terminations.get(terminal.token());

            if (delivery == null || !delivery.terminal().equals(terminal)) {
                return CompletableFuture.completedFuture(null);
            }

            terminations.remove(terminal.token());
        }

        return delivery.dispatch(requester);
    }

    @Override
    public synchronized RoomMatchToken resultToken(RoomMembership membership) {
        var receipt = receipts.get(membership.sessionId());
        return receipt != null && receipt.membership().equals(membership) ? receipt.token() : null;
    }

    public synchronized boolean consumeResult(RoomMatchContext context) {
        return context.token().equals(resultToken(context.membership()));
    }

    @Override
    public synchronized void release(RoomMembership membership) {
        var receipt = receipts.get(membership.sessionId());

        if (receipt != null && receipt.membership().equals(membership)) {
            receipts.remove(membership.sessionId());
        }
    }

    public synchronized Optional<MatchSnapshot> current(RoomMatchToken token) {
        return Optional.ofNullable(find(token)).map(MatchSnapshot::from);
    }

    private Match find(RoomMatchToken token) {
        var runtime = matches.get(new MatchId(token.matchId()));
        var match = runtime == null ? null : runtime.match;
        return match != null && match.roomId().equals(token.roomId())
            && match.roomGeneration().equals(token.generation())
            ? match : null;
    }

    private static RoomMatchReservationResult result(
        RoomMatchReservationOutcome outcome,
        RoomMatchToken token,
        String reason
    ) {
        return new RoomMatchReservationResult(outcome, reason, token);
    }

    private static void log(MatchLogEvent event, RoomMatchToken token, String result) {
        LOG.log(
            event,
            matchId(token.matchId()),
            roomId(token.roomId().value()),
            roomGeneration(token.generation()),
            reason(result)
        );
    }

    private static void log(MatchLogEvent event, RoomMatchDraft draft, String result) {
        LOG.log(
            event,
            matchId(draft.token().matchId()),
            roomId(draft.token().roomId().value()),
            roomGeneration(draft.token().generation()),
            sessionId(draft.ownerSessionId()),
            channelId(draft.channelId()),
            mapId(draft.settings().mapId()),
            roomMode(draft.settings().mode().name()),
            reason(result)
        );
    }
}
