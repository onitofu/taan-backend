package taan.online.room.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.outcome;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;
import static taan.online.logging.LogField.team;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.ChangeRoomTeamOutcome;
import taan.online.room.service.model.ChangeRoomTeamResult;
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
import taan.online.room.service.model.SlotAvailabilityOutcome;
import taan.online.room.service.model.SlotAvailabilityResult;

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
        return membership(id).map(member -> RoomSnapshot.from(rooms.get(member.roomId())));
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

        var ownerCharacter = owner.character().id();
        var stale = memberships.values().stream()
            .filter(value -> participant(value).character().id().equals(ownerCharacter)).toList();

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
        var result = RoomSnapshot.from(room);
        var delivery = RoomNotifications.deliver(subscription, () -> output.initialize(result));
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
        var result = RoomSnapshot.from(room);
        var delivery = RoomNotifications.deliver(subscription, () -> output.initialize(result));
        var recipients = List.copyOf(memberships.values());
        int index = occupied.orElseThrow().index();
        RoomNotifications.update(recipients, result, participant.sessionId(), index, false);
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

    public synchronized SlotAvailabilityResult setSlotAvailability(UUID id, int index, RoomSlotState desiredState) {
        String rejection = controlRejection(id);

        if (rejection != null) {
            var outcome = inactive(rejection) ? SlotAvailabilityOutcome.IGNORED : SlotAvailabilityOutcome.REJECTED;
            return new SlotAvailabilityResult(outcome, rejection);
        }

        var subscription = memberships.get(id);
        var member = subscription.membership();
        var room = rooms.get(member.roomId());

        if (!room.ownerSessionId().equals(id)) {
            return new SlotAvailabilityResult(SlotAvailabilityOutcome.REJECTED, "NOT_OWNER");
        }

        if (room.state() != RoomState.WAITING) {
            return new SlotAvailabilityResult(SlotAvailabilityOutcome.REJECTED, "ROOM_UNAVAILABLE");
        }

        if (index < 0 || index > 7 || desiredState == null || desiredState == RoomSlotState.OCCUPIED) {
            return new SlotAvailabilityResult(SlotAvailabilityOutcome.REJECTED, "INVALID_SLOT_AVAILABILITY");
        }

        if (room.slots().get(index).participant() != null) {
            return new SlotAvailabilityResult(SlotAvailabilityOutcome.REJECTED, "OCCUPIED_SLOT");
        }

        var actor = participant(subscription);
        boolean changed = room.setSlotAvailability(index, desiredState);
        var outcome = changed ? SlotAvailabilityOutcome.CHANGED : SlotAvailabilityOutcome.UNCHANGED;
        var recipients = List.copyOf(memberships.values());
        var delivery = RoomNotifications.slotAvailability(recipients, member, index, desiredState, changed);

        LOG.log(
            desiredState == RoomSlotState.OPEN ? RoomLogEvent.SLOT_OPENED : RoomLogEvent.SLOT_CLOSED,
            sessionId(id),
            accountId(actor.character().accountId().value()),
            characterId(actor.character().id().value()),
            channelId(room.channelId()),
            roomId(room.id().value()),
            slotIndex(index),
            outcome(outcome.name())
        );
        return new SlotAvailabilityResult(outcome, outcome.name(), delivery);
    }

    public synchronized ChangeRoomTeamResult changeTeam(UUID id, RoomTeam desiredTeam) {
        String rejection = controlRejection(id);

        if (rejection != null) {
            var outcome = inactive(rejection) ? ChangeRoomTeamOutcome.IGNORED : ChangeRoomTeamOutcome.REJECTED;
            return new ChangeRoomTeamResult(outcome, rejection);
        }

        var subscription = memberships.get(id);
        var member = subscription.membership();
        var room = rooms.get(member.roomId());

        if (desiredTeam == null || room.state() != RoomState.WAITING) {
            return new ChangeRoomTeamResult(ChangeRoomTeamOutcome.REJECTED, "INVALID_TEAM_OR_PHASE");
        }

        var source = room.slots().stream().filter(slot -> slot.participant() != null)
            .filter(slot -> slot.participant().sessionId().equals(id)).findFirst().orElseThrow();

        if (source.team() == desiredTeam) {
            LOG.log(RoomLogEvent.TEAM_UNCHANGED, sessionId(id), roomId(room.id().value()), team(desiredTeam.name()));
            return new ChangeRoomTeamResult(ChangeRoomTeamOutcome.UNCHANGED, "ALREADY_ON_TEAM");
        }

        var changed = room.changeTeam(id, desiredTeam);

        if (changed.isEmpty()) {
            return new ChangeRoomTeamResult(ChangeRoomTeamOutcome.REJECTED, "NO_OPEN_TEAM_SLOT");
        }

        var change = changed.orElseThrow();
        var recipients = List.copyOf(memberships.values());
        var delivery = RoomNotifications.teamChanged(recipients, member, change);
        return new ChangeRoomTeamResult(ChangeRoomTeamOutcome.CHANGED, "CHANGED", delivery);
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

    private String controlRejection(UUID id) {
        var subscription = memberships.get(id);

        if (phase == RoomRegistryPhase.CLOSED || subscription == null) {
            return phase == RoomRegistryPhase.CLOSED ? "SERVER_STOPPING" : "NO_MEMBERSHIP";
        }

        var context = channels.membership(id).orElse(null);

        if (context == null) {
            return "SESSION_CLOSED";
        }

        return context.channelId() != subscription.membership().channelId()
            || context.phase() != ChannelPresencePhase.MEMBER || !matches(context, participant(subscription))
            ? "CONTEXT_CHANGED" : null;
    }

    private boolean inactive(String rejection) {
        return rejection.equals("SERVER_STOPPING") || rejection.equals("SESSION_CLOSED");
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
        var previous = RoomSnapshot.from(room);
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

        var result = RoomSnapshot.from(room);
        boolean ownerChanged = !previous.ownerSessionId().equals(result.ownerSessionId());
        var recipients = List.copyOf(memberships.values());
        RoomNotifications.update(recipients, result, expected.sessionId(), removed.index(), ownerChanged);
        publish(room.channelId());
        channels.roomChanged(room.channelId(), room.id().value());

        if (ownerChanged) {
            log(RoomLogEvent.OWNER_CHANGED, result, result.owner(), closeReason);
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
            .sorted(Comparator.comparingInt(room -> room.id().value())).map(RoomListing::from).toList();

        if (snapshot.isEmpty()) {
            updated.remove(channelId);
        } else {
            updated.put(channelId, snapshot);
        }

        listings = Map.copyOf(updated);
    }

    private void log(RoomLogEvent event, RoomSnapshot room, RoomParticipant actor, String closeReason) {
        int slot = room.slots().stream().filter(value -> actor.equals(value.participant()))
            .findFirst().orElseThrow().index();
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
