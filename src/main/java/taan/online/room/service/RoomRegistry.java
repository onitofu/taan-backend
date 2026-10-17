package taan.online.room.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.recipientSessionId;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.service.ChannelRegistry;
import taan.online.channel.service.model.ChannelMembershipSnapshot;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomIdPool;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomPassword;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomSlot;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.room.service.model.CreateRoomResult;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.service.model.EnterRoomResult;
import taan.online.room.service.model.LeaveRoomOutcome;
import taan.online.room.service.model.LeaveRoomResult;
import taan.online.room.service.model.RoomEntryTarget;
import taan.online.room.service.model.RoomLeaveReceipt;
import taan.online.room.service.model.RoomListing;
import taan.online.room.service.model.RoomMembership;
import taan.online.room.service.model.RoomRegistryPhase;
import taan.online.room.service.model.RoomSnapshot;

public final class RoomRegistry implements RoomListingAccess, AutoCloseable {
    private static final EventLogger LOG = EventLogger.forClass(RoomRegistry.class);

    private final ChannelRegistry channels;
    private final RoomIdPool ids;
    private final Map<RoomId, Room> rooms = new HashMap<>();
    private final Map<UUID, RoomSubscription> memberships = new HashMap<>();
    private final Map<UUID, RoomLeaveReceipt> receipts = new HashMap<>();
    private volatile Map<Integer, List<RoomListing>> listings = Map.of();
    private RoomRegistryPhase phase = RoomRegistryPhase.OPEN;

    public RoomRegistry(ChannelRegistry channels) {
        this(channels, new RoomIdPool());
    }

    RoomRegistry(ChannelRegistry channels, RoomIdPool ids) {
        this.channels = channels;
        this.ids = ids;
    }

    @Override
    public List<RoomListing> forChannel(int channelId) {
        return listings.getOrDefault(channelId, List.of());
    }

    public synchronized Optional<RoomSnapshot> current(UUID id) {
        return membership(id).map(member -> snapshot(rooms.get(member.roomId())));
    }

    public synchronized Optional<RoomMembership> membership(UUID id) {
        return Optional.ofNullable(memberships.get(id)).map(RoomSubscription::membership);
    }

    public synchronized Optional<RoomEntryTarget> entryTarget(ChannelMembershipSnapshot context, int requestedId) {
        var room = rooms.values().stream().filter(value -> value.id().value() == requestedId)
            .filter(value -> value.channelId() == context.channelId()).findFirst();
        return room.map(value -> new RoomEntryTarget(value.id(), value.generation(), value.channelId()));
    }

    public synchronized CreateRoomResult create(
        ChannelMembershipSnapshot context,
        RoomSettings settings,
        RoomPassword password,
        RoomParticipant owner,
        RoomPresenceOutput output
    ) {
        if (phase == RoomRegistryPhase.CLOSED) {
            return new CreateRoomResult(CreateRoomOutcome.IGNORED, null, "SERVER_STOPPING");
        }

        UUID id = owner.sessionId();

        if (memberships.containsKey(id)) {
            return rejected("ALREADY_IN_ROOM");
        }

        if (!current(context, owner)) {
            return rejected("CHANNEL_CHANGED");
        }

        if (!matches(context, owner)) {
            return rejected("CHARACTER_CHANGED");
        }

        var stale = memberships.values().stream().filter(
            value -> participant(value).character().id()
                .equals(owner.character().id())
        ).toList();

        for (var previous : stale) {
            release(previous.membership(), "STALE_SESSION");
        }

        var allocated = ids.allocate();

        if (allocated.isEmpty()) {
            return rejected("ROOM_IDS_EXHAUSTED");
        }

        var room = new Room(allocated.orElseThrow(), UUID.randomUUID(), context.channelId(), settings, password, owner);
        rooms.put(room.id(), room);
        var subscription = subscribe(room, owner, output);
        var result = snapshot(room);
        var delivery = deliver(subscription, () -> output.initialize(result));
        publish(room.channelId());
        channels.hideFromLobby(id);
        channels.roomChanged(room.channelId(), room.id().value());
        log(RoomLogEvent.CREATION_SUCCEEDED, result, owner, "CREATED");
        return new CreateRoomResult(CreateRoomOutcome.CREATED, result, "CREATED", delivery);
    }

    public synchronized EnterRoomResult enter(
        ChannelMembershipSnapshot context,
        RoomEntryTarget target,
        RoomParticipant participant,
        String password,
        RoomPresenceOutput output
    ) {
        if (phase == RoomRegistryPhase.CLOSED) {
            return entryRejected(EnterRoomOutcome.IGNORED, "SERVER_STOPPING");
        }

        if (memberships.containsKey(participant.sessionId())) {
            return entryRejected(EnterRoomOutcome.ALREADY_IN_ROOM, "ALREADY_IN_ROOM");
        }

        if (!current(context, participant) || !matches(context, participant)) {
            return entryRejected(EnterRoomOutcome.REJECTED, "CONTEXT_CHANGED");
        }

        var room = rooms.get(target.id());

        if (room == null || !room.generation().equals(target.generation()) || room.channelId() != context.channelId()
            || target.channelId() != context.channelId() || room.state() != RoomState.WAITING) {
            return entryRejected(EnterRoomOutcome.NOT_FOUND, "ROOM_UNAVAILABLE");
        }

        if (room.contains(participant)) {
            return entryRejected(EnterRoomOutcome.ALREADY_IN_ROOM, "ALREADY_IN_ROOM");
        }

        if (room.playerCount() >= room.settings().capacity()) {
            return entryRejected(EnterRoomOutcome.FULL, "ROOM_FULL");
        }

        if (!room.acceptsPassword(password)) {
            return entryRejected(EnterRoomOutcome.WRONG_PASSWORD, "WRONG_PASSWORD");
        }

        var occupied = room.enter(participant, password);

        if (occupied.isEmpty()) {
            return entryRejected(EnterRoomOutcome.NO_SLOT, "NO_OPEN_SLOT");
        }

        var subscription = subscribe(room, participant, output);
        var result = snapshot(room);
        var delivery = deliver(subscription, () -> output.initialize(result));
        notifyMembers(result, participant.sessionId(), occupied.orElseThrow().index(), false);
        publish(room.channelId());
        channels.hideFromLobby(participant.sessionId());
        channels.roomChanged(room.channelId(), room.id().value());
        log(RoomLogEvent.ENTRY_SUCCEEDED, result, participant, "ENTERED");
        return new EnterRoomResult(EnterRoomOutcome.ENTERED, "ENTERED", delivery);
    }

    public synchronized EnterRoomResult quickJoin(
        ChannelMembershipSnapshot context,
        RoomParticipant participant,
        RoomPresenceOutput output
    ) {
        if (phase == RoomRegistryPhase.CLOSED) {
            return entryRejected(EnterRoomOutcome.IGNORED, "SERVER_STOPPING");
        }

        if (memberships.containsKey(participant.sessionId())) {
            return entryRejected(EnterRoomOutcome.ALREADY_IN_ROOM, "ALREADY_IN_ROOM");
        }

        if (!current(context, participant) || !matches(context, participant)) {
            return entryRejected(EnterRoomOutcome.REJECTED, "CONTEXT_CHANGED");
        }

        var target = rooms.values().stream()
            .filter(room -> room.channelId() == context.channelId() && room.state() == RoomState.WAITING)
            .filter(room -> !room.protectedRoom() && room.playerCount() < room.settings().capacity())
            .filter(room -> room.slots().stream().anyMatch(slot -> slot.state() == RoomSlotState.OPEN))
            .filter(room -> !room.contains(participant))
            .min(Comparator.comparingInt(Room::playerCount).reversed().thenComparingInt(room -> room.id().value()));

        if (target.isEmpty()) {
            return entryRejected(EnterRoomOutcome.NOT_FOUND, "NO_MATCHING_ROOM");
        }

        var room = target.orElseThrow();
        return enter(
            context,
            new RoomEntryTarget(room.id(), room.generation(), room.channelId()),
            participant,
            "",
            output
        );
    }

    public synchronized LeaveRoomResult leave(UUID id, int requestedRoomId, int requestedReason) {
        var member = membership(id).orElse(null);

        if (requestedReason != 0) {
            return rejectLeave(id, "UNSUPPORTED_REASON");
        }

        if (member == null || member.roomId().value() != requestedRoomId) {
            var receipt = receipts.get(id);

            if (receipt != null && receipt.membership().roomId().value() == requestedRoomId) {
                LOG.log(RoomLogEvent.LEAVE_REPLAYED, sessionId(id), roomId(receipt.membership().roomId().value()));
                return new LeaveRoomResult(LeaveRoomOutcome.REPLAYED);
            }

            return rejectLeave(id, "UNKNOWN_MEMBERSHIP");
        }

        var context = channels.membership(id);

        if (context.isEmpty()) {
            release(id, "SESSION_CLOSED");
            return new LeaveRoomResult(LeaveRoomOutcome.IGNORED);
        }

        if (context.orElseThrow().channelId() != member.channelId()) {
            return rejectLeave(id, "CHANNEL_CHANGED");
        }

        remove(member, "LEFT");
        receipts.put(id, new RoomLeaveReceipt(member));
        return new LeaveRoomResult(LeaveRoomOutcome.LEFT);
    }

    public synchronized void release(UUID id, String closeReason) {
        membership(id).ifPresent(member -> remove(member, closeReason));
        receipts.remove(id);
    }

    public synchronized void release(RoomMembership expected, String closeReason) {
        remove(expected, closeReason);
    }

    @Override
    public synchronized void close() {
        phase = RoomRegistryPhase.CLOSED;

        for (var value : List.copyOf(memberships.values())) {
            remove(value.membership(), "SERVER_STOPPING");
        }

        receipts.clear();
    }

    private RoomSubscription subscribe(Room room, RoomParticipant participant, RoomPresenceOutput output) {
        var membership = new RoomMembership(
            participant.sessionId(),
            room.id(),
            room.generation(),
            room.channelId(),
            UUID.randomUUID()
        );
        var subscription = new RoomSubscription(membership, output);
        memberships.put(participant.sessionId(), subscription);
        receipts.remove(participant.sessionId());
        return subscription;
    }

    private boolean current(ChannelMembershipSnapshot context, RoomParticipant participant) {
        return context.phase() == ChannelPresencePhase.LOBBY
            && channels.membership(participant.sessionId()).filter(context::equals).isPresent();
    }

    private boolean matches(ChannelMembershipSnapshot context, RoomParticipant participant) {
        var actor = context.participant();
        return actor.accountId().equals(participant.character().accountId())
            && actor.characterId().equals(participant.character().id());
    }

    private RoomParticipant participant(RoomSubscription subscription) {
        return rooms.get(subscription.membership().roomId()).slots().stream().map(RoomSlot::participant)
            .filter(value -> value != null && value.sessionId().equals(subscription.membership().sessionId()))
            .findFirst().orElseThrow();
    }

    private void remove(RoomMembership expected, String closeReason) {
        if (membership(expected.sessionId()).filter(expected::equals).isEmpty()) {
            return;
        }

        var room = rooms.get(expected.roomId());
        var previous = snapshot(room);
        var removed = room.leave(expected.sessionId()).orElseThrow();
        memberships.remove(expected.sessionId());
        log(RoomLogEvent.LEFT, previous, removed.participant(), closeReason);

        if (room.playerCount() == 0) {
            rooms.remove(room.id());
            ids.release(room.id());
            publish(room.channelId());
            channels.roomsReplaced(room.channelId());
            log(RoomLogEvent.DESTROYED, previous, removed.participant(), "EMPTY");
            return;
        }

        var result = snapshot(room);
        boolean ownerChanged = !previous.ownerSessionId().equals(result.ownerSessionId());
        notifyMembers(result, expected.sessionId(), removed.index(), ownerChanged);
        publish(room.channelId());
        channels.roomChanged(room.channelId(), room.id().value());

        if (ownerChanged) {
            log(RoomLogEvent.OWNER_CHANGED, result, result.owner(), closeReason);
        }
    }

    private void notifyMembers(RoomSnapshot snapshot, UUID excluded, int changedSlot, boolean ownerChanged) {
        var recipients = memberships.values().stream().filter(
            value -> value.membership().roomId().equals(snapshot.id())
        )
            .filter(value -> !value.membership().sessionId().equals(excluded)).toList();

        for (var recipient : recipients) {
            deliver(recipient, () -> recipient.output().update(snapshot, List.of(changedSlot), ownerChanged));
        }
    }

    private CompletionStage<Void> deliver(RoomSubscription recipient, Supplier<CompletionStage<Void>> sending) {
        try {
            return sending.get();
        } catch (RuntimeException failure) {
            var member = recipient.membership();
            LOG.failure(
                RoomLogEvent.NOTIFICATION_FAILED,
                failure,
                roomId(member.roomId().value()),
                channelId(member.channelId()),
                recipientSessionId(member.sessionId())
            );
            recipient.output().close("ROOM_NOTIFICATION_FAILED");
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CreateRoomResult rejected(String value) {
        return new CreateRoomResult(CreateRoomOutcome.REJECTED, null, value);
    }

    private EnterRoomResult entryRejected(EnterRoomOutcome outcome, String reason) {
        return new EnterRoomResult(outcome, reason, CompletableFuture.completedFuture(null));
    }

    private LeaveRoomResult rejectLeave(UUID id, String value) {
        LOG.log(RoomLogEvent.LEAVE_REJECTED, sessionId(id), reason(value));
        return new LeaveRoomResult(LeaveRoomOutcome.REJECTED);
    }

    private void publish(int channelId) {
        var updated = new HashMap<>(listings);
        var snapshot = rooms.values().stream().filter(room -> room.channelId() == channelId)
            .sorted(Comparator.comparingInt(room -> room.id().value())).map(
                room -> new RoomListing(
                    room.id(),
                    room.channelId(),
                    room.settings().title(),
                    room.state(),
                    room.settings().mode(),
                    room.settings().rule(),
                    room.playerCount(),
                    room.settings().capacity(),
                    room.settings().mapId(),
                    room.protectedRoom()
                )
            ).toList();

        if (snapshot.isEmpty()) {
            updated.remove(channelId);
        } else {
            updated.put(channelId, snapshot);
        }

        listings = Map.copyOf(updated);
    }

    private RoomSnapshot snapshot(Room room) {
        return new RoomSnapshot(
            room.id(),
            room.generation(),
            room.channelId(),
            room.settings(),
            room.ownerSessionId(),
            room.slots(),
            room.protectedRoom()
        );
    }

    private void log(RoomLogEvent event, RoomSnapshot room, RoomParticipant actor, String closeReason) {
        int slot = room.slots().stream().filter(
            value -> actor.equals(value.participant())
        ).findFirst().orElseThrow().index();
        LOG.log(
            event,
            sessionId(actor.sessionId()),
            accountId(actor.character().accountId().value()),
            characterId(actor.character().id().value()),
            channelId(room.channelId()),
            roomId(room.id().value()),
            slotIndex(slot),
            reason(closeReason)
        );
    }
}
