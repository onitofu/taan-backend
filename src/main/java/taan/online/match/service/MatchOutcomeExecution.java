package taan.online.match.service;

import static taan.online.logging.LogField.admissionId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.outcomeCause;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomGeneration;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.turnRevision;
import static taan.online.logging.LogField.winningTeam;
import static taan.online.logging.LogField.worldRevision;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;
import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.Match;
import taan.online.match.model.MatchExitKind;
import taan.online.match.model.MatchOutcome;
import taan.online.match.model.MatchOutcomeCause;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.DepartMatchCommand;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.MatchTurnToken;
import taan.online.match.service.model.TerminateMatchCommand;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTerminationResult;

final class MatchOutcomeExecution {
    private static final EventLogger LOG = EventLogger.forClass(MatchOutcomeExecution.class);
    private final MatchRuntime owner;
    private final Match match;
    private final Object admission;
    private final LongSupplier clock;
    private final CompletableFuture<RoomMatchTerminationResult> terminal = new CompletableFuture<>();
    private UUID requester;
    private volatile RoomMatchTerminationResult result = RoomMatchTerminationResult.ignored();

    MatchOutcomeExecution(MatchRuntime owner, Match match, Object admission, LongSupplier clock) {
        this.owner = owner;
        this.match = match;
        this.admission = admission;
        this.clock = clock;
    }

    boolean end(TerminateMatchCommand command) {
        if (command.reason() != RoomMatchEndReason.GIVE_UP || !owner.live() || !playing()) {
            return cancel(command.reason(), command.context().sessionId());
        }

        requester = command.context().sessionId();
        return exit(requester, MatchExitKind.SURRENDERED);
    }

    CompletionStage<RoomMatchTerminationResult> result() {
        return terminal;
    }

    RoomMatchTerminationResult current() {
        return result;
    }

    boolean acceptsDeparture() {
        var phase = owner.snapshot().phase();
        return owner.live() && (phase == MatchPhase.ACTION || phase == MatchPhase.SETTLING
            || phase == MatchPhase.COMPLETION);
    }

    boolean depart(DepartMatchCommand command) {
        if (command.reason() != RoomMatchEndReason.DISCONNECTED && command.reason() != RoomMatchEndReason.ROOM_LEAVE) {
            return false;
        }

        var c = command.context();
        var snapshot = owner.snapshot();

        if (!owner.running() || !owner.live() || !playing() || !c.token().equals(owner.progress().match())
            || c.channelId() != snapshot.channelId() || snapshot.participants().stream().noneMatch(p ->
                p.sessionId().equals(c.sessionId()) && p.admissionId().equals(c.admissionId()))) {
            return false;
        }

        LOG.log(
            MatchLogEvent.DEPARTURE_ACCEPTED,
            matchId(snapshot.id().value()),
            sessionId(c.sessionId()),
            admissionId(c.admissionId()),
            reason(command.reason().name())
        );
        boolean accepted = exit(c.sessionId(), MatchExitKind.DEPARTED);

        if (accepted && command.reason() == RoomMatchEndReason.ROOM_LEAVE) {
            owner.recipients().stream().filter(p -> p.sessionId().equals(c.sessionId()))
                .forEach(p -> p.output().close("MATCH_ROOM_LEAVE"));
        }

        return accepted;
    }

    private boolean playing() {
        return match.phase() == MatchPhase.ACTION || match.phase() == MatchPhase.SETTLING
            || match.phase() == MatchPhase.COMPLETION;
    }

    private boolean exit(UUID session, MatchExitKind kind) {
        if (match.phase() == MatchPhase.COMPLETION && !match.completion().orElseThrow().awaitingSessions().isEmpty()
            && clock.getAsLong() - match.completion().orElseThrow().deadlineNanos() >= 0) {
            owner.turns.expired(
                new MatchTurnToken(owner.progress().match(), match.turn().revision(), match.phaseRevision()),
                match.completion().map(value -> value.fingerprint())
            );
            return false;
        }

        var before = owner.snapshot();

        synchronized (admission) {
            if (!owner.running()) {
                return false;
            }

            match.withdraw(session, kind);
            owner.refresh();
        }

        decided(before, owner.snapshot());
        boolean nobody = match.resolution().exits().size() == match.participants().size()
            && match.resolution().exits().values().stream().allMatch(value -> value == MatchExitKind.DEPARTED);

        if (nobody) {
            finish();
        } else if (match.phase() == MatchPhase.COMPLETION) {
            owner.turns.finish();
        } else if (match.phase() == MatchPhase.SETTLING) {
            owner.movement.expired();
        } else {
            finish();
        }

        return true;
    }

    void finish() {
        var outcome = match.resolution().pendingOutcome().orElseThrow();
        var reason = outcome.cause() == MatchOutcomeCause.ELIMINATION ? RoomMatchEndReason.PARTICIPANT_ELIMINATED
            : outcome.cause() == MatchOutcomeCause.SURRENDER ? RoomMatchEndReason.GIVE_UP
                : RoomMatchEndReason.DISCONNECTED;
        cancel(reason, requester);
    }

    boolean cancel(RoomMatchEndReason reason, UUID requester) {
        complete(owner.notifyTermination(reason, requester));
        return result.termination() != null;
    }

    void complete(RoomMatchTerminationResult value) {
        if (terminal.complete(value)) {
            result = value;
        }
    }

    static MatchOutcome terminal(MatchSnapshot snapshot, boolean live, RoomMatchEndReason reason) {
        boolean gameplay = reason == RoomMatchEndReason.PARTICIPANT_ELIMINATED || reason == RoomMatchEndReason.GIVE_UP
            || reason == RoomMatchEndReason.DISCONNECTED || reason == RoomMatchEndReason.ROOM_LEAVE;
        return live && gameplay ? snapshot.resolution().pendingOutcome().orElse(MatchOutcome.previewAborted())
            : MatchOutcome.previewAborted();
    }

    static void decided(MatchSnapshot before, MatchSnapshot after) {
        if (after.resolution().pendingOutcome().isPresent()
            && !before.resolution().pendingOutcome().equals(after.resolution().pendingOutcome())) {
            log(MatchLogEvent.OUTCOME_DECIDED, after, after.resolution().pendingOutcome().orElseThrow());
        }
    }

    static void finished(MatchSnapshot snapshot, MatchOutcome outcome, RoomMatchEndReason reason) {
        if (!outcome.winningTeams().isEmpty()) {
            log(MatchLogEvent.FINISHED, snapshot, outcome);
        } else {
            LOG.log(
                MatchLogEvent.CANCELLED,
                matchId(snapshot.id().value()),
                roomId(snapshot.roomId().value()),
                roomGeneration(snapshot.roomGeneration()),
                channelId(snapshot.channelId()),
                outcomeCause(outcome.cause().name()),
                reason(reason.name())
            );
        }
    }

    private static void log(MatchLogEvent event, MatchSnapshot snapshot, MatchOutcome outcome) {
        LOG.log(
            event,
            matchId(snapshot.id().value()),
            roomId(snapshot.roomId().value()),
            roomGeneration(snapshot.roomGeneration()),
            channelId(snapshot.channelId()),
            turnRevision(snapshot.turn().revision()),
            worldRevision(snapshot.world().revision()),
            winningTeam(outcome.winningTeams().iterator().next().name()),
            outcomeCause(outcome.cause().name())
        );
    }
}
