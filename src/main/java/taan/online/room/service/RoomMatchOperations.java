package taan.online.room.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import taan.online.channel.service.ChannelRegistry;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.inventory.service.model.InventoryMoveContext;
import taan.online.room.model.CaptainSelectionPhase;
import taan.online.room.model.CaptainSelectionResult;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.RoomMatchDraft;
import taan.online.room.service.model.RoomMatchParticipant;
import taan.online.room.service.model.RoomMatchReservationOutcome;
import taan.online.room.service.model.RoomMatchReservationResult;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomRegistryPhase;

final class RoomMatchOperations {
    private final ChannelRegistry channels;
    private final Map<RoomId, Room> rooms;
    private final Map<UUID, RoomSubscription> memberships;
    private final RoomStartOperations starts;
    private final RoomMatchLifecycle lifecycle;
    private final Consumer<Room> changed;

    RoomMatchOperations(
        ChannelRegistry channels,
        Map<RoomId, Room> rooms,
        Map<UUID, RoomSubscription> memberships,
        RoomStartOperations starts,
        RoomMatchLifecycle lifecycle,
        Consumer<Room> changed
    ) {
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

    RoomMatchReservationResult commit(RoomMatchToken token) {
        var room = current(token);

        if (room == null) {
            return stale();
        }

        String rejection = contextRejection(room);

        if (rejection != null) {
            cancel(room, rejection);
            return rejected(rejection);
        }

        return lifecycle.commit(token);
    }

    RoomMatchReservationResult rollback(RoomMatchToken token, String reason) {
        var room = current(token);
        return room == null ? stale() : cancel(room, reason);
    }

    RoomMatchReservationResult cancel(Room room, String reason) {
        if (room.matchId() == null) {
            return stale();
        }

        var token = token(room);
        var previous = room.slots();
        lifecycle.cancel(token, reason);
        room.rollbackMatch(token.matchId());
        changed.accept(room);

        for (var slot : previous) {
            if (slot.state() == RoomSlotState.READY) {
                var participant = slot.participant();
                RoomNotifications.readyChanged(
                    List.copyOf(memberships.values()),
                    memberships.get(participant.sessionId()).membership(),
                    participant.character().accountId(),
                    false
                );
            }
        }

        return result(RoomMatchReservationOutcome.ROLLED_BACK, token, reason);
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
