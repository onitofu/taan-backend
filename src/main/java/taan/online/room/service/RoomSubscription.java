package taan.online.room.service;

import java.util.UUID;
import taan.online.room.model.Room;
import taan.online.room.model.RoomParticipant;
import taan.online.room.service.model.RoomMembership;

record RoomSubscription(RoomMembership membership, RoomPresenceOutput output) {
    static RoomSubscription create(Room room, RoomParticipant participant, RoomPresenceOutput output) {
        return new RoomSubscription(
            new RoomMembership(
                participant.sessionId(),
                room.id(),
                room.generation(),
                room.channelId(),
                UUID.randomUUID()
            ),
            output
        );
    }
}
