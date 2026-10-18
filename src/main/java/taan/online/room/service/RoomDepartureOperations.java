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
import taan.online.room.service.model.RoomMembership;
import taan.online.room.service.model.RoomSnapshot;

final class RoomDepartureOperations {
    private final ChannelRegistry channels;
    private final RoomIdPool ids;
    private final Map<RoomId, Room> rooms;
    private final Map<UUID, RoomSubscription> memberships;
    private final RoomStartOperations starts;
    private final RoomMatchOperations matches;
    private final Consumer<Integer> publish;

    RoomDepartureOperations(
        ChannelRegistry channels,
        RoomIdPool ids,
        Map<RoomId, Room> rooms,
        Map<UUID, RoomSubscription> memberships,
        RoomStartOperations starts,
        RoomMatchOperations matches,
        Consumer<Integer> publish
    ) {
        this.channels = channels;
        this.ids = ids;
        this.rooms = rooms;
        this.memberships = memberships;
        this.starts = starts;
        this.matches = matches;
        this.publish = publish;
    }

    Room room(UUID id) {
        var subscription = memberships.get(id);
        return subscription == null ? null : rooms.get(subscription.membership().roomId());
    }

    void remove(RoomMembership expected, String closeReason) {
        if (memberships.get(expected.sessionId()) == null
            || !memberships.get(expected.sessionId()).membership().equals(expected)) {
            return;
        }

        var room = rooms.get(expected.roomId());
        matches.cancel(room, closeReason);
        starts.cancel(room, closeReason);
        var previous = RoomSnapshot.from(room);
        var removed = room.leave(expected.sessionId()).orElseThrow();
        memberships.remove(expected.sessionId());
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
