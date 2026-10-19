package taan.online.room.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import taan.online.channel.service.ChannelRegistry;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomIdPool;
import taan.online.room.service.model.LeaveRoomOutcome;
import taan.online.room.service.model.LeaveRoomResult;
import taan.online.room.service.model.RoomLeaveReceipt;
import taan.online.room.service.model.RoomMembership;
import taan.online.room.service.model.RoomSnapshot;

final class RoomDepartureOperations {
    private final ChannelRegistry channels;
    private final RoomIdPool ids;
    private final Map<RoomId, Room> rooms;
    private final Map<UUID, RoomSubscription> memberships;
    private final Map<UUID, RoomLeaveReceipt> receipts;
    private final RoomStartOperations starts;
    private final RoomMatchOperations matches;
    private final Consumer<Integer> publish;

    RoomDepartureOperations(
        ChannelRegistry channels,
        RoomIdPool ids,
        Map<RoomId, Room> rooms,
        Map<UUID, RoomSubscription> memberships,
        Map<UUID, RoomLeaveReceipt> receipts,
        RoomStartOperations starts,
        RoomMatchOperations matches,
        Consumer<Integer> publish
    ) {
        this.channels = channels;
        this.ids = ids;
        this.rooms = rooms;
        this.memberships = memberships;
        this.receipts = receipts;
        this.starts = starts;
        this.matches = matches;
        this.publish = publish;
    }

    Room room(UUID id) {
        var subscription = memberships.get(id);
        return subscription == null ? null : rooms.get(subscription.membership().roomId());
    }

    LeaveRoomResult leave(UUID id, int requestedRoomId, int requestedReason) {
        var subscription = memberships.get(id);
        var member = subscription == null ? null : subscription.membership();

        if (requestedReason != 0) {
            return RoomNotifications.leaveRejected(id, "UNSUPPORTED_REASON");
        }

        if (member == null || member.roomId().value() != requestedRoomId) {
            var receipt = receipts.get(id);

            if (receipt != null && receipt.membership().roomId().value() == requestedRoomId) {
                receipts.put(id, new RoomLeaveReceipt(receipt.membership()));
                return RoomNotifications.leaveReplayed(id, receipt);
            }

            return RoomNotifications.leaveRejected(id, "UNKNOWN_MEMBERSHIP");
        }

        var context = channels.membership(id);

        if (context.isEmpty()) {
            remove(member, "SESSION_CLOSED");
            receipts.remove(id);
            return new LeaveRoomResult(LeaveRoomOutcome.IGNORED);
        }

        if (context.orElseThrow().channelId() != member.channelId()) {
            return RoomNotifications.leaveRejected(id, "CHANNEL_CHANGED");
        }

        boolean closing = matches.leave(rooms.get(member.roomId()), id);
        remove(member, "LEFT");

        if (closing) {
            return new LeaveRoomResult(LeaveRoomOutcome.IGNORED);
        }

        receipts.put(id, new RoomLeaveReceipt(member));
        return new LeaveRoomResult(LeaveRoomOutcome.LEFT);
    }

    void remove(RoomMembership expected, String closeReason) {
        if (memberships.get(expected.sessionId()) == null
            || !memberships.get(expected.sessionId()).membership().equals(expected)) {
            return;
        }

        var room = rooms.get(expected.roomId());
        matches.departed(room, expected.sessionId(), closeReason);

        if (matches.deferDeparture(room, () -> remove(expected, closeReason))) {
            return;
        }

        starts.cancel(room, closeReason);
        var previous = RoomSnapshot.from(room);
        var removed = room.leave(expected.sessionId()).orElseThrow();
        memberships.remove(expected.sessionId());
        matches.forget(expected);
        RoomNotifications.log(RoomLogEvent.LEFT, previous, removed.participant(), closeReason);

        if (room.playerCount() == 0) {
            rooms.remove(room.id());
            ids.release(room.id());
            publish.accept(room.channelId());
            channels.roomsReplaced(room.channelId());
            RoomNotifications.log(RoomLogEvent.DESTROYED, previous, removed.participant(), "EMPTY");
            return;
        }

        var result = RoomSnapshot.from(room);
        publish.accept(room.channelId());
        channels.roomChanged(room.channelId(), room.id().value());
        RoomNotifications.departure(
            List.copyOf(memberships.values()),
            previous,
            result,
            expected.sessionId(),
            removed.index(),
            closeReason
        );
    }
}
