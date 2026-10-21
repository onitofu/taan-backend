package taan.online.match.service;

import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.recipientSessionId;
import static taan.online.logging.LogField.roomGeneration;
import static taan.online.logging.LogField.roomId;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.MatchExitKind;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.MatchResult;
import taan.online.match.service.model.MatchEndResult;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchTerminationMode;
import taan.online.room.service.model.RoomMembership;

final class MatchTerminationDelivery {
    private static final EventLogger LOG = EventLogger.forClass(MatchTerminationDelivery.class);

    private final RoomMatchTermination terminal;
    private final List<MatchParticipant> participants;
    private final List<MatchSubscription> recipients;
    private final MatchSubscription departing;

    MatchTerminationDelivery(
        RoomMatchTermination terminal,
        List<MatchSubscription> recipients,
        MatchSubscription departing,
        List<MatchParticipant> participants
    ) {
        this.terminal = terminal;
        this.participants = List.copyOf(participants);
        this.recipients = List.copyOf(recipients);
        this.departing = departing;
    }

    static List<RoomMembership> memberships(MatchSnapshot snapshot, RoomMatchTerminationMode mode, UUID departing) {
        return snapshot.participants().stream()
            .filter(value -> mode != RoomMatchTerminationMode.RETURN_TO_ROOM || !value.sessionId().equals(departing)
                && snapshot.resolution().exits().get(value.sessionId()) != MatchExitKind.DEPARTED)
            .map(
                value -> new RoomMembership(
                    value.sessionId(),
                    snapshot.roomId(),
                    snapshot.roomGeneration(),
                    snapshot.channelId(),
                    value.admissionId()
                )
            ).toList();
    }

    static RoomMatchTerminationMode mode(MatchPhase phase, RoomMatchEndReason reason, boolean unpublishedStart) {
        if (reason == RoomMatchEndReason.SERVER_STOPPING) {
            return RoomMatchTerminationMode.DISCONNECT;
        }

        if (phase == MatchPhase.RESERVED || unpublishedStart && reason == RoomMatchEndReason.PREPARATION_FAILED) {
            return RoomMatchTerminationMode.ROLLBACK;
        }

        return phase == MatchPhase.PREVIEW || phase == MatchPhase.ACTION || phase == MatchPhase.SETTLING
            || phase == MatchPhase.COMPLETION
            ? RoomMatchTerminationMode.RETURN_TO_ROOM
            : RoomMatchTerminationMode.DISCONNECT;
    }

    RoomMatchTermination terminal() {
        return terminal;
    }

    CompletionStage<Void> dispatch(UUID requester, MatchResult stored) {
        CompletionStage<Void> result = CompletableFuture.completedFuture(null);

        for (var recipient : recipients) {
            if (terminal.mode() == RoomMatchTerminationMode.RETURN_TO_ROOM) {
                var delivery = deliver(recipient, new MatchEndResult(stored, participants, recipient.sessionId()));

                if (recipient.sessionId().equals(requester)) {
                    result = delivery;
                }
            } else {
                recipient.output().close("MATCH_" + terminal.reason().name());
            }
        }

        if (departing != null) {
            departing.output().close("MATCH_ROOM_LEAVE");
        }

        return result;
    }

    void settlementFailed() {
        recipients.forEach(recipient -> recipient.output().close("MATCH_SETTLEMENT_FAILED"));

        if (departing != null) {
            departing.output().close("MATCH_SETTLEMENT_FAILED");
        }
    }

    private CompletionStage<Void> deliver(MatchSubscription recipient, MatchEndResult result) {
        try {
            return recipient.output().end(result).handle(
                LogContext.propagate(
                    (ignored, failure) -> {
                        if (failure != null) {
                            failed(recipient, failure);
                        }

                        return null;
                    }
                )
            );
        } catch (RuntimeException failure) {
            failed(recipient, failure);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void failed(MatchSubscription recipient, Throwable failure) {
        var token = terminal.token();
        LOG.failure(
            MatchLogEvent.TERMINAL_DELIVERY_FAILED,
            failure,
            matchId(token.matchId()),
            roomId(token.roomId().value()),
            roomGeneration(token.generation()),
            recipientSessionId(recipient.sessionId()),
            reason("END_DELIVERY_FAILED")
        );
        recipient.output().close("MATCH_END_DELIVERY_FAILED");
    }
}
