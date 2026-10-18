package taan.online.room.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.outcome;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.selectionId;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;
import static taan.online.logging.LogField.team;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import taan.online.channel.service.ChannelRegistry;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.CaptainSelectionPhase;
import taan.online.room.model.CaptainSelectionResult;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.RoomRegistryPhase;
import taan.online.room.service.model.RoomSelectionToken;
import taan.online.room.service.model.RoomStartOutcome;
import taan.online.room.service.model.RoomStartResult;
import taan.online.room.service.model.SetCaptainCandidateOutcome;
import taan.online.room.service.model.SetCaptainCandidateResult;

final class RoomStartOperations {
    private static final EventLogger LOG = EventLogger.forClass(RoomStartOperations.class);
    private static final String UNAVAILABLE = "Starting a match is currently unavailable.";

    private final ChannelRegistry channels;
    private final Map<RoomId, Room> rooms;
    private final Map<UUID, RoomSubscription> memberships;
    private final RoomSelectionScheduler scheduler;
    private final LongSupplier clock;
    private final RandomGenerator random;
    private final Consumer<Room> changed;
    private final AdvanceCaptainSelectionAction advanceAction;

    RoomStartOperations(
        ChannelRegistry channels,
        Map<RoomId, Room> rooms,
        Map<UUID, RoomSubscription> memberships,
        RoomSelectionScheduler scheduler,
        LongSupplier clock,
        RandomGenerator random,
        Consumer<Room> changed,
        AdvanceCaptainSelectionAction advanceAction
    ) {
        this.channels = channels;
        this.rooms = rooms;
        this.memberships = memberships;
        this.scheduler = scheduler;
        this.clock = clock;
        this.random = random;
        this.changed = changed;
        this.advanceAction = advanceAction;
    }

    RoomStartResult begin(UUID id, String rejection) {
        if (rejection != null) {
            return new RoomStartResult(RoomStartOutcome.IGNORED, rejection);
        }

        var room = room(id);
        advance(room);

        rejection = room.startRejection(id);

        if (rejection == null && room.state() == RoomState.CAPTAIN_SELECTION) {
            return new RoomStartResult(RoomStartOutcome.ALREADY_SELECTING, "ALREADY_SELECTING");
        }

        if (rejection == null && !room.settings().mode().requiresCaptainSelection()) {
            rejection = "NOT_CAPTAIN_MODE";
        }

        if (rejection == null && !room.captainSelectionAvailable(clock.getAsLong())) {
            rejection = "PREVIOUS_WINDOW_ACTIVE";
        }

        if (rejection != null) {
            return rejected(room, id, rejection);
        }

        room.beginCaptainSelection(UUID.randomUUID(), clock.getAsLong());
        var selection = room.captainSelection();
        var token = new RoomSelectionToken(room.id(), room.generation(), selection.id());

        try {
            scheduler.schedule(token, 5_000_000_000L, () -> advanceAction.execute(token));
            scheduler.schedule(token, 10_000_000_000L, () -> advanceAction.execute(token));
        } catch (RuntimeException failure) {
            cancel(room, "SCHEDULING_FAILED");
            throw failure;
        }

        changed.accept(room);
        log(RoomLogEvent.CAPTAIN_OPENED, room, id, selection.id(), "OPENED");
        var delivery = broadcast(room, id, output -> output.captainSelectionOpened());
        return new RoomStartResult(RoomStartOutcome.SELECTION_OPENED, "OPENED", null, delivery);
    }

    RoomStartResult start(UUID id, String rejection) {
        if (rejection != null) {
            return new RoomStartResult(RoomStartOutcome.IGNORED, rejection);
        }

        var room = room(id);
        advance(room);
        rejection = room.startRejection(id);

        if (rejection != null) {
            return rejected(room, id, rejection);
        }

        CaptainSelectionResult captains = null;

        if (room.settings().mode().requiresCaptainSelection()) {
            var selection = room.captainSelection();

            if (selection == null) {
                return rejected(room, id, "CAPTAIN_SELECTION_REQUIRED");
            }

            if (selection.phase() != CaptainSelectionPhase.FINALIZED) {
                return rejected(room, id, "CAPTAIN_SELECTION_IN_PROGRESS");
            }

            captains = selection.result();
        }

        log(RoomLogEvent.START_CHECKED, room, id, null, "VALIDATED");
        return new RoomStartResult(
            RoomStartOutcome.VALIDATED,
            "VALIDATED",
            captains,
            CompletableFuture.completedFuture(null)
        );
    }

    RoomStartResult unavailable(UUID id, String reason, CaptainSelectionResult captains) {
        var room = room(id);
        if (room.captainSelection() != null) {
            finish(room);
        }

        log(RoomLogEvent.START_DEFERRED, room, id, null, reason);
        var delivery = broadcast(room, id, output -> output.notice(UNAVAILABLE));
        return new RoomStartResult(RoomStartOutcome.MATCH_UNAVAILABLE, reason, captains, delivery);
    }

    SetCaptainCandidateResult candidate(UUID id, long characterId, boolean requested, String rejection) {
        if (rejection != null) {
            return new SetCaptainCandidateResult(
                SetCaptainCandidateOutcome.IGNORED,
                rejection,
                false,
                CompletableFuture.completedFuture(null)
            );
        }

        var room = room(id);
        advance(room);
        var slot = room.slots().stream().filter(value -> value.participant() != null)
            .filter(value -> value.participant().sessionId().equals(id)).findFirst().orElseThrow();
        var selection = room.captainSelection();
        boolean current = selection != null && selection.candidate(slot.index());

        if (slot.participant().character().id().value() != characterId) {
            log(RoomLogEvent.CAPTAIN_CANDIDATE_SET, room, id, null, "CHARACTER_MISMATCH");
            return new SetCaptainCandidateResult(
                SetCaptainCandidateOutcome.REJECTED,
                "CHARACTER_MISMATCH",
                current,
                notice(id, "Captain selection request rejected.")
            );
        }

        if (selection == null || selection.phase() != CaptainSelectionPhase.SELECTING) {
            var subscription = memberships.get(id);
            var delivery = RoomNotifications.deliver(
                subscription,
                () -> subscription.output().captainCandidateChanged(characterId, current)
            );
            return new SetCaptainCandidateResult(
                SetCaptainCandidateOutcome.REJECTED,
                "SELECTION_CLOSED",
                current,
                delivery
            );
        }

        boolean updated = selection.setCandidate(slot.index(), requested);
        log(RoomLogEvent.CAPTAIN_CANDIDATE_SET, room, id, selection.id(), requested ? "CANDIDATE" : "CANCELLED");
        var delivery = broadcast(room, id, output -> output.captainCandidateChanged(characterId, requested));
        return new SetCaptainCandidateResult(
            updated ? SetCaptainCandidateOutcome.CHANGED : SetCaptainCandidateOutcome.UNCHANGED,
            updated ? "CHANGED" : "UNCHANGED",
            requested,
            delivery
        );
    }

    void failed(RoomSelectionToken token, RuntimeException failure) {
        var room = selectionRoom(token);

        if (room != null) {
            LOG.failure(
                RoomLogEvent.CAPTAIN_FAILED,
                failure,
                roomId(room.id().value()),
                selectionId(token.selectionId())
            );
            cancel(room, "INTERNAL_ERROR");
        }
    }

    void advance(RoomSelectionToken token) {
        var room = selectionRoom(token);

        if (room != null) {
            advance(room);
        }
    }

    private Room selectionRoom(RoomSelectionToken token) {
        var room = rooms.get(token.roomId());
        return room != null && room.generation().equals(token.generation()) && room.captainSelection() != null
            && room.captainSelection().id().equals(token.selectionId()) ? room : null;
    }

    void advance(Room room) {
        if (room == null || room.captainSelection() == null) {
            return;
        }

        var selection = room.captainSelection();
        long now = clock.getAsLong();
        boolean valid = room.slots().stream().filter(slot -> slot.participant() != null).allMatch(
            slot -> channels.membership(slot.participant().sessionId()).filter(
                context -> RoomControlChecks.rejection(
                    RoomRegistryPhase.OPEN,
                    memberships.get(slot.participant().sessionId()).membership(),
                    context,
                    slot.participant()
                ) == null
            ).isPresent()
        );

        if (!valid) {
            cancel(room, "CONTEXT_CHANGED");
        } else if (selection.expired(now)) {
            log(RoomLogEvent.CAPTAIN_EXPIRED, room, room.ownerSessionId(), selection.id(), "START_TIMEOUT");
            finish(room);
            broadcast(room, null, output -> output.notice("Captain selection expired. Start again."));
        } else if (selection.selectionEnded(now) && selection.phase() == CaptainSelectionPhase.SELECTING) {
            var result = selection.finalizeSelection(room.slots(), random);
            finalized(room, selection.id(), "BLUE", result.blueLeaderSlot());
            finalized(room, selection.id(), "RED", result.redLeaderSlot());
        }
    }

    void cancel(Room room, String reason) {
        if (room.captainSelection() == null) {
            return;
        }

        log(RoomLogEvent.CAPTAIN_CANCELLED, room, room.ownerSessionId(), room.captainSelection().id(), reason);
        finish(room);
        broadcast(room, null, output -> output.notice("Captain selection cancelled. Start again."));
    }

    void finishReservedSelection(Room room, CaptainSelectionResult captains) {
        if (captains != null) {
            scheduler.cancel(room.captainSelection().id());
            room.finishCaptainSelection();
        }
    }

    private void finish(Room room) {
        scheduler.cancel(room.captainSelection().id());
        room.finishCaptainSelection();
        changed.accept(room);
    }

    private RoomStartResult rejected(Room room, UUID id, String reason) {
        log(RoomLogEvent.START_REJECTED, room, id, null, reason);
        return new RoomStartResult(
            RoomStartOutcome.REJECTED,
            reason,
            null,
            notice(id, "The room is not ready to start.")
        );
    }

    private CompletionStage<Void> notice(UUID id, String text) {
        var subscription = memberships.get(id);
        return RoomNotifications.deliver(subscription, () -> subscription.output().notice(text));
    }

    private CompletionStage<Void> broadcast(
        Room room,
        UUID actor,
        Function<RoomPresenceOutput, CompletionStage<Void>> sending
    ) {
        CompletionStage<Void> delivery = CompletableFuture.completedFuture(null);

        for (var subscription : List.copyOf(memberships.values())) {
            if (subscription.membership().roomId().equals(room.id())) {
                var sent = RoomNotifications.deliver(subscription, () -> sending.apply(subscription.output()));

                if (subscription.membership().sessionId().equals(actor)) {
                    delivery = sent;
                }
            }
        }

        return delivery;
    }

    private Room room(UUID id) {
        return rooms.get(memberships.get(id).membership().roomId());
    }

    private void finalized(Room room, UUID selection, String team, int index) {
        LOG.log(
            RoomLogEvent.CAPTAIN_FINALIZED,
            roomId(room.id().value()),
            channelId(room.channelId()),
            selectionId(selection),
            team(team),
            slotIndex(index)
        );
    }

    private void log(RoomLogEvent event, Room room, UUID id, UUID selection, String result) {
        var actor = memberships.get(id);
        var participant = room.slots().stream().filter(value -> value.participant() != null)
            .map(value -> value.participant()).filter(value -> value.sessionId().equals(id)).findFirst().orElseThrow();
        LOG.log(
            event,
            sessionId(actor.membership().sessionId()),
            accountId(participant.character().accountId().value()),
            characterId(participant.character().id().value()),
            roomId(room.id().value()),
            channelId(room.channelId()),
            selectionId(selection),
            outcome(result),
            reason(result)
        );
    }
}
