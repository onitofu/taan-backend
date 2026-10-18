package taan.online.room.service;

import java.util.UUID;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.service.model.ChannelMembershipSnapshot;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomSlotState;
import taan.online.room.service.model.RoomMembership;
import taan.online.room.service.model.RoomRegistryPhase;
import taan.online.room.service.model.RoomSnapshot;

final class RoomControlChecks {
    private RoomControlChecks() {
    }

    static String rejection(
        RoomRegistryPhase phase,
        RoomMembership membership,
        ChannelMembershipSnapshot context,
        RoomParticipant participant
    ) {
        if (phase == RoomRegistryPhase.CLOSED || membership == null) {
            return phase == RoomRegistryPhase.CLOSED ? "SERVER_STOPPING" : "NO_MEMBERSHIP";
        }

        if (context == null) {
            return "SESSION_CLOSED";
        }

        return context.channelId() != membership.channelId()
            || context.phase() != ChannelPresencePhase.MEMBER || !matches(context, participant)
            ? "CONTEXT_CHANGED" : null;
    }

    static String availability(RoomSnapshot room, UUID id, int index, RoomSlotState desiredState) {
        if (!room.ownerSessionId().equals(id)) {
            return "NOT_OWNER";
        }

        if (index < 0 || index > 7 || desiredState == null || desiredState.occupied()) {
            return "INVALID_SLOT_AVAILABILITY";
        }

        return room.slots().get(index).participant() != null ? "OCCUPIED_SLOT" : null;
    }

    static boolean inactive(String rejection) {
        return rejection.equals("SERVER_STOPPING") || rejection.equals("SESSION_CLOSED");
    }

    static boolean matches(ChannelMembershipSnapshot context, RoomParticipant participant) {
        var actor = context.participant();
        return actor.accountId().equals(participant.character().accountId())
            && actor.characterId().equals(participant.character().id());
    }
}
