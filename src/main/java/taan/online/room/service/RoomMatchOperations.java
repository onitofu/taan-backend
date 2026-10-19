package taan.online.room.service;

import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.roomGeneration;
import static taan.online.logging.LogField.roomId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import taan.online.channel.service.ChannelRegistry;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.inventory.service.model.InventoryMoveContext;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.CaptainSelectionPhase;
import taan.online.room.model.CaptainSelectionResult;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchDraft;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchParticipant;
import taan.online.room.service.model.RoomMatchReservationOutcome;
import taan.online.room.service.model.RoomMatchReservationResult;
import taan.online.room.service.model.RoomMatchTerminationMode;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;
import taan.online.room.service.model.RoomRegistryPhase;

final class RoomMatchOperations {
    private static final EventLogger LOG = EventLogger.forClass(RoomMatchOperations.class);

    private final Object monitor;
    private final Map<RoomMatchToken, CompletableFuture<Void>> pending = new HashMap<>();
    private final ChannelRegistry channels;
    private final Map<RoomId, Room> rooms;
    private final Map<UUID, RoomSubscription> memberships;
    private final RoomStartOperations starts;
    private final RoomMatchLifecycle lifecycle;
    private final Consumer<Room> changed;

    RoomMatchOperations(
        Object monitor,
        ChannelRegistry channels,
        Map<RoomId, Room> rooms,
        Map<UUID, RoomSubscription> memberships,
        RoomStartOperations starts,
        RoomMatchLifecycle lifecycle,
        Consumer<Room> changed
    ) {
        this.monitor = monitor;
        this.channels = channels;
        this.rooms = rooms;
        this.memberships = memberships;
        this.starts = starts;
        this.lifecycle = lifecycle;
        this.changed = changed;
    }

    InventoryMoveContext inventoryContext(InventoryActor actor, RoomRegistryPhase phase) {
        var subscription = memberships.get(actor.sessionId());
        var room = subscription == null ? null : rooms.get(subscription.membership().roomId());
        starts.advance(room);
        return RoomControlChecks.inventoryContext(
            actor,
            phase,
            subscription,
            channels.membership(actor.sessionId()).orElse(null),
            room
        );
    }

    RoomMatchReservationResult reserve(UUID id, String rejection) {
        if (rejection != null) {
            return rejected(rejection);
        }

        var room = rooms.get(memberships.get(id).membership().roomId());

        if (room.state() == RoomState.MATCH && room.ownerSessionId().equals(id)) {
            return result(RoomMatchReservationOutcome.UNCHANGED, token(room), "ALREADY_RESERVED");
        }

        rejection = room.startRejection(id);

        if (rejection == null) {
            rejection = contextRejection(room);
        }

        if (rejection != null) {
            return rejected(rejection);
        }

        CaptainSelectionResult captains = null;

        if (room.settings().mode().requiresCaptainSelection()) {
            var selection = room.captainSelection();

            if (selection == null) {
                return rejected("CAPTAIN_SELECTION_REQUIRED");
            }

            if (selection.phase() != CaptainSelectionPhase.FINALIZED) {
                return rejected("CAPTAIN_SELECTION_IN_PROGRESS");
            }

            captains = selection.result();
        }

        if (lifecycle == null) {
            return rejected("MATCH_UNAVAILABLE");
        }

        var token = new RoomMatchToken(room.id(), room.generation(), UUID.randomUUID());
        var roster = room.slots().stream().filter(slot -> slot.participant() != null).map(
            slot -> new RoomMatchParticipant(memberships.get(slot.participant().sessionId()).membership(), slot)
        ).toList();
        var draft = new RoomMatchDraft(
            token,
            room.channelId(),
            room.ownerSessionId(),
            room.settings(),
            roster,
            captains
        );

        try {
            var result = lifecycle.reserve(draft);

            if (result.outcome() != RoomMatchReservationOutcome.RESERVED) {
                return result;
            }

            room.reserveMatch(token.matchId());
            starts.finishReservedSelection(room, draft.captains());
            changed.accept(room);
            return result;
        } catch (RuntimeException failure) {
            lifecycle.cancel(token, "PREPARATION_FAILED");
            room.rollbackMatch(token.matchId());
            changed.accept(room);
            throw failure;
        }
    }

    String startRejection(RoomMatchToken token) {
        var room = current(token);
        return room == null ? "STALE_MATCH" : contextRejection(room);
    }

    CompletionStage<RoomMatchReservationResult> commit(RoomMatchToken token) {
        String rejection = startRejection(token);

        if (rejection != null) {
            var room = current(token);

            if (room != null) {
                cancel(room, rejection);
            }

            return CompletableFuture.completedFuture(room == null ? stale() : rejected(rejection));
        }

        return lifecycle.commit(token);
    }

    RoomMatchReservationResult rollback(RoomMatchToken token, String reason) {
        var room = current(token);
        return room == null ? stale() : cancel(room, reason);
    }

    RoomMatchReservationResult cancel(Room room, String reason) {
        var ended = finish(room, RoomMatchEndReason.PREPARATION_FAILED, null, null);
        return ended.termination() == null ? stale()
            : result(RoomMatchReservationOutcome.ROLLED_BACK, ended.termination().token(), reason);
    }

    RoomMatchContext context(UUID id, RoomRegistryPhase phase) {
        var subscription = memberships.get(id);

        if (subscription == null) {
            return null;
        }

        var member = subscription.membership();
        var room = rooms.get(member.roomId());
        var participant = RoomControlChecks.participant(rooms, subscription);

        if (RoomControlChecks.rejection(phase, member, channels.membership(id).orElse(null), participant) != null) {
            return null;
        }

        var token = room.matchId() != null ? token(room) : lifecycle == null ? null : lifecycle.resultToken(member);
        return token == null ? null : new RoomMatchContext(member, token);
    }

    boolean activeOwner(UUID id) {
        var room = rooms.get(memberships.get(id).membership().roomId());
        return room.state() == RoomState.MATCH && room.ownerSessionId().equals(id);
    }

    boolean activate(RoomMatchToken token, RoomMatchCallbacks callbacks) {
        return lifecycle.activate(token, callbacks);
    }

    CompletionStage<Void> publish(RoomMatchToken token) {
        return lifecycle.publish(token);
    }

    RoomMatchTerminationResult terminate(
        RoomMatchContext expected,
        RoomMatchEndReason reason,
        RoomRegistryPhase phase
    ) {
        var actual = context(expected.membership().sessionId(), phase);
        return expected.equals(actual) ? terminate(expected.token(), reason, expected.membership().sessionId())
            : RoomMatchTerminationResult.ignored();
    }

    RoomMatchTerminationResult terminate(RoomMatchToken token, RoomMatchEndReason reason, UUID requester) {
        var room = current(token);
        return room == null ? RoomMatchTerminationResult.ignored() : finish(room, reason, null, requester);
    }

    boolean leave(Room room, UUID id) {
        var ended = finish(room, RoomMatchEndReason.ROOM_LEAVE, id, null);
        return ended.termination() != null && ended.termination().mode() != RoomMatchTerminationMode.ROLLBACK;
    }

    void departed(Room room, UUID id, String reason) {
        finish(
            room,
            reason.equals("SERVER_STOPPING") ? RoomMatchEndReason.SERVER_STOPPING
                : RoomMatchEndReason.DISCONNECTED,
            id,
            null
        );
    }

    boolean deferDeparture(Room room, Runnable departure) {
        var ending = pending.get(room.matchId() == null ? null : token(room));

        if (ending == null) {
            return false;
        }

        ending.thenRun(
            () -> {
                synchronized (monitor) {
                    departure.run();
                }
            }
        );
        return true;
    }

    void forget(RoomMembership expected) {
        if (lifecycle != null) {
            lifecycle.release(expected);
        }
    }

    private RoomMatchTerminationResult finish(Room room, RoomMatchEndReason reason, UUID departing, UUID requester) {
        if (room.matchId() == null) {
            return RoomMatchTerminationResult.ignored();
        }

        var token = token(room);

        if (pending.containsKey(token)) {
            return RoomMatchTerminationResult.ignored();
        }

        var previous = room.slots();
        var terminal = lifecycle.prepareTermination(token, reason, departing);

        if (terminal == null) {
            lifecycle.cancel(token, "MATCH_CONTEXT_LOST");
            room.rollbackMatch(token.matchId());
            changed.accept(room);
            LOG.failure(
                RoomLogEvent.OPERATION_FAILED,
                new IllegalStateException("Match runtime missing"),
                roomId(room.id().value()),
                roomGeneration(room.generation()),
                matchId(token.matchId())
            );
            List.copyOf(memberships.values()).stream()
                .filter(value -> value.membership().roomId().equals(room.id()))
                .forEach(value -> value.output().close("MATCH_CONTEXT_LOST"));
            return RoomMatchTerminationResult.ignored();
        }

        var queued = new CompletableFuture<Void>();
        pending.put(token, queued);
        Runnable returned = () -> {
            synchronized (monitor) {
                if (current(token) == room) {
                    room.rollbackMatch(token.matchId());
                    changed.accept(room);

                    for (var slot : previous) {
                        if (slot.state() == RoomSlotState.READY) {
                            var participant = slot.participant();
                            var subscription = memberships.get(participant.sessionId());

                            RoomNotifications.readyChanged(
                                List.copyOf(memberships.values()),
                                subscription.membership(),
                                participant.character().accountId(),
                                false
                            );
                        }
                    }
                }

                pending.remove(token);
                queued.complete(null);
            }
        };

        if (terminal.mode() == RoomMatchTerminationMode.ROLLBACK) {
            returned.run();
        }

        var delivery = lifecycle.dispatchTermination(
            terminal,
            requester,
            terminal.mode() == RoomMatchTerminationMode.ROLLBACK ? () -> { } : returned
        );

        return new RoomMatchTerminationResult(terminal, delivery);
    }

    private Room current(RoomMatchToken token) {
        var room = rooms.get(token.roomId());
        return room != null && room.generation().equals(token.generation()) && token.matchId().equals(room.matchId())
            ? room : null;
    }

    private String contextRejection(Room room) {
        for (var slot : room.slots()) {
            if (slot.participant() != null) {
                var id = slot.participant().sessionId();
                String rejection = RoomControlChecks.rejection(
                    RoomRegistryPhase.OPEN,
                    memberships.get(id).membership(),
                    channels.membership(id).orElse(null),
                    slot.participant()
                );

                if (rejection != null) {
                    return rejection;
                }
            }
        }

        return null;
    }

    private static RoomMatchToken token(Room room) {
        return new RoomMatchToken(room.id(), room.generation(), room.matchId());
    }

    private static RoomMatchReservationResult rejected(String reason) {
        return result(RoomMatchReservationOutcome.REJECTED, null, reason);
    }

    private static RoomMatchReservationResult stale() {
        return result(RoomMatchReservationOutcome.STALE, null, "STALE_MATCH");
    }

    private static RoomMatchReservationResult result(
        RoomMatchReservationOutcome outcome,
        RoomMatchToken token,
        String reason
    ) {
        return new RoomMatchReservationResult(outcome, reason, token);
    }
}
