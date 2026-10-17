package taan.online.room.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.sessionId;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.service.ChannelRegistry;
import taan.online.channel.service.model.ChannelMembershipSnapshot;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomIdPool;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomSettings;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.room.service.model.CreateRoomResult;
import taan.online.room.service.model.LeaveRoomOutcome;
import taan.online.room.service.model.LeaveRoomResult;
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
    private final Map<UUID, RoomMembership> memberships = new HashMap<>();
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
        var membership = memberships.get(id);
        return membership == null ? Optional.empty() : Optional.of(snapshot(rooms.get(membership.roomId())));
    }

    public synchronized Optional<RoomMembership> membership(UUID id) {
        return Optional.ofNullable(memberships.get(id));
    }

    public synchronized CreateRoomResult create(
        ChannelMembershipSnapshot context,
        RoomSettings settings,
        RoomParticipant owner
    ) {
        if (phase == RoomRegistryPhase.CLOSED) {
            return new CreateRoomResult(CreateRoomOutcome.IGNORED, null, "SERVER_STOPPING");
        }

        UUID id = owner.sessionId();

        if (memberships.containsKey(id)) {
            return rejected("ALREADY_IN_ROOM");
        }

        if (context.phase() != ChannelPresencePhase.LOBBY
            || channels.membership(id).filter(context::equals).isEmpty()) {
            return rejected("CHANNEL_CHANGED");
        }

        var actor = context.participant();

        if (!actor.accountId().equals(owner.character().accountId())
            || !actor.characterId().equals(owner.character().id())) {
            return rejected("CHARACTER_CHANGED");
        }

        var stale = memberships.values().stream().filter(
            value -> rooms.get(value.roomId()).owner().character().id().equals(owner.character().id())
        ).toList();

        for (var previous : stale) {
            release(previous.sessionId(), "STALE_SESSION");
        }

        var allocated = ids.allocate();

        if (allocated.isEmpty()) {
            return rejected("ROOM_IDS_EXHAUSTED");
        }

        var room = new Room(allocated.orElseThrow(), UUID.randomUUID(), context.channelId(), settings, owner);
        rooms.put(room.id(), room);
        memberships.put(id, new RoomMembership(id, room.id(), room.generation(), room.channelId()));

        publish(room.channelId());
        channels.hideFromLobby(id);
        channels.roomChanged(room.channelId(), room.id().value());

        var result = snapshot(room);
        log(RoomLogEvent.CREATION_SUCCEEDED, result, "CREATED");
        return new CreateRoomResult(CreateRoomOutcome.CREATED, result, "CREATED");
    }

    public synchronized LeaveRoomResult leave(UUID id, int requestedRoomId, int requestedReason) {
        var member = memberships.get(id);

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

        var removed = remove(member).orElseThrow();
        receipts.put(id, new RoomLeaveReceipt(member));
        log(RoomLogEvent.LEFT, removed, "LEFT");
        log(RoomLogEvent.DESTROYED, removed, "EMPTY");
        return new LeaveRoomResult(LeaveRoomOutcome.LEFT);
    }

    public synchronized void release(UUID id, String closeReason) {
        var member = memberships.get(id);

        if (member != null) {
            release(member, closeReason);
        }

        receipts.remove(id);
    }

    public synchronized void release(RoomMembership expected, String closeReason) {
        remove(expected).ifPresent(value -> log(RoomLogEvent.DESTROYED, value, closeReason));
    }

    @Override
    public synchronized void close() {
        phase = RoomRegistryPhase.CLOSED;

        for (var member : List.copyOf(memberships.values())) {
            release(member, "SERVER_STOPPING");
        }

        receipts.clear();
    }

    private CreateRoomResult rejected(String value) {
        return new CreateRoomResult(CreateRoomOutcome.REJECTED, null, value);
    }

    private LeaveRoomResult rejectLeave(UUID id, String value) {
        LOG.log(RoomLogEvent.LEAVE_REJECTED, sessionId(id), reason(value));
        return new LeaveRoomResult(LeaveRoomOutcome.REJECTED);
    }

    private Optional<RoomSnapshot> remove(RoomMembership expected) {
        if (!expected.equals(memberships.get(expected.sessionId()))) {
            return Optional.empty();
        }

        var room = rooms.remove(expected.roomId());
        var snapshot = snapshot(room);
        room.close();
        memberships.remove(expected.sessionId());
        ids.release(room.id());
        publish(room.channelId());
        channels.roomsReplaced(room.channelId());
        return Optional.of(snapshot);
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
                    1,
                    room.settings().capacity(),
                    room.settings().mapId()
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
        return new RoomSnapshot(room.id(), room.generation(), room.channelId(), room.settings(), room.owner());
    }

    private void log(RoomLogEvent event, RoomSnapshot room, String closeReason) {
        var owner = room.owner();
        LOG.log(
            event,
            sessionId(owner.sessionId()),
            accountId(owner.character().accountId().value()),
            characterId(owner.character().id().value()),
            channelId(room.channelId()),
            roomId(room.id().value()),
            reason(closeReason)
        );
    }
}
