package taan.online.room.service;

import java.util.UUID;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.service.model.ChannelMembershipSnapshot;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.inventory.service.model.InventoryMoveContext;
import taan.online.room.model.Room;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.RoomLeaveReceipt;
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

    static InventoryMoveContext inventoryContext(
        InventoryActor actor,
        RoomRegistryPhase phase,
        RoomSubscription subscription,
        ChannelMembershipSnapshot channel,
        Room room
    ) {
        if (subscription == null) {
            return new InventoryMoveContext(actor, null, phase == RoomRegistryPhase.OPEN);
        }

        var slot = room.slots().stream().filter(value -> value.participant() != null)
            .filter(value -> value.participant().sessionId().equals(actor.sessionId())).findFirst().orElseThrow();
        var participant = slot.participant();
        var character = participant.character();
        boolean allowed = rejection(phase, subscription.membership(), channel, participant) == null
            && room.state() == RoomState.WAITING && slot.state() != RoomSlotState.READY
            && character.accountId().equals(actor.accountId()) && character.id().value() == actor.characterId()
            && character.gender() == actor.gender();
        return new InventoryMoveContext(actor, subscription.membership().admissionId(), allowed);
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

    static String expel(Room room, UUID actor, RoomParticipant target) {
        if (!room.ownerSessionId().equals(actor)) {
            return "NOT_OWNER";
        }

        if (room.state() != RoomState.WAITING) {
            return "ROOM_UNAVAILABLE";
        }

        if (target == null) {
            return "TARGET_NOT_FOUND";
        }

        return room.owner().character().accountId().equals(target.character().accountId()) ? "OWN_ACCOUNT" : null;
    }

    static boolean pending(RoomLeaveReceipt receipt) {
        return receipt != null && receipt.awaitingAcknowledgement();
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
