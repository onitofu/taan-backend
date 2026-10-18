package taan.online.room.service;

import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.team;

import java.util.List;
import java.util.UUID;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.Room;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomSlot;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomState;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.ChangeRoomTeamOutcome;
import taan.online.room.service.model.ChangeRoomTeamResult;
import taan.online.room.service.model.ChangeRoomWeaponOutcome;
import taan.online.room.service.model.ChangeRoomWeaponResult;
import taan.online.room.service.model.RoomSnapshot;
import taan.online.room.service.model.SetRoomReadyOutcome;
import taan.online.room.service.model.SetRoomReadyResult;
import taan.online.room.service.model.SlotAvailabilityOutcome;
import taan.online.room.service.model.SlotAvailabilityResult;

final class RoomControlOperations {
    private static final EventLogger LOG = EventLogger.forClass(RoomControlOperations.class);

    private RoomControlOperations() {
    }

    static SlotAvailabilityResult setSlotAvailability(
        Room room,
        RoomSubscription subscription,
        List<RoomSubscription> subscriptions,
        int index,
        RoomSlotState desiredState
    ) {
        var member = subscription.membership();
        var id = member.sessionId();

        if (room.state() != RoomState.WAITING) {
            return new SlotAvailabilityResult(SlotAvailabilityOutcome.REJECTED, "ROOM_UNAVAILABLE");
        }

        String rejection = RoomControlChecks.availability(RoomSnapshot.from(room), id, index, desiredState);

        if (rejection != null) {
            return new SlotAvailabilityResult(SlotAvailabilityOutcome.REJECTED, rejection);
        }

        var actor = participant(room, id);
        boolean changed = room.setSlotAvailability(index, desiredState);
        var outcome = changed ? SlotAvailabilityOutcome.CHANGED : SlotAvailabilityOutcome.UNCHANGED;
        var delivery = RoomNotifications.slotAvailability(subscriptions, member, index, desiredState, changed);

        RoomNotifications.availabilityLog(room.channelId(), room.id(), actor, index, desiredState, outcome.name());
        return new SlotAvailabilityResult(outcome, outcome.name(), delivery);
    }

    static ChangeRoomTeamResult changeTeam(
        Room room,
        RoomSubscription subscription,
        List<RoomSubscription> subscriptions,
        RoomTeam desiredTeam
    ) {
        var member = subscription.membership();
        var id = member.sessionId();

        if (desiredTeam == null || room.state() != RoomState.WAITING) {
            return new ChangeRoomTeamResult(ChangeRoomTeamOutcome.REJECTED, "INVALID_TEAM_OR_PHASE");
        }

        var source = room.slots().stream().filter(slot -> slot.participant() != null)
            .filter(slot -> slot.participant().sessionId().equals(id)).findFirst().orElseThrow();

        if (source.state() == RoomSlotState.READY) {
            return new ChangeRoomTeamResult(ChangeRoomTeamOutcome.REJECTED, "PARTICIPANT_READY");
        }

        if (source.team() == desiredTeam) {
            LOG.log(RoomLogEvent.TEAM_UNCHANGED, sessionId(id), roomId(room.id().value()), team(desiredTeam.name()));
            return new ChangeRoomTeamResult(ChangeRoomTeamOutcome.UNCHANGED, "ALREADY_ON_TEAM");
        }

        var changed = room.changeTeam(id, desiredTeam);

        if (changed.isEmpty()) {
            return new ChangeRoomTeamResult(ChangeRoomTeamOutcome.REJECTED, "NO_OPEN_TEAM_SLOT");
        }

        var change = changed.orElseThrow();
        var delivery = RoomNotifications.teamChanged(subscriptions, member, change);
        return new ChangeRoomTeamResult(ChangeRoomTeamOutcome.CHANGED, "CHANGED", delivery);
    }

    static SetRoomReadyResult setReady(
        Room room,
        RoomSubscription subscription,
        List<RoomSubscription> subscriptions,
        boolean ready
    ) {
        var member = subscription.membership();
        var id = member.sessionId();

        if (room.state() != RoomState.WAITING) {
            return new SetRoomReadyResult(SetRoomReadyOutcome.REJECTED, "ROOM_UNAVAILABLE");
        }

        var account = participant(room, id).character().accountId();

        if (ready && room.ownerSessionId().equals(id)) {
            var delivery = RoomNotifications.deliver(
                subscription,
                () -> subscription.output().readyChanged(account, false)
            );
            return new SetRoomReadyResult(SetRoomReadyOutcome.REJECTED, "OWNER_CANNOT_BE_READY", delivery);
        }

        boolean changed = room.setReady(account, ready);
        var outcome = changed ? SetRoomReadyOutcome.CHANGED : SetRoomReadyOutcome.UNCHANGED;
        var delivery = RoomNotifications.readyChanged(subscriptions, member, account, ready);
        RoomNotifications.readyLog(RoomLogEvent.READY_SET, RoomSnapshot.from(room), id, ready, outcome.name(), null);
        return new SetRoomReadyResult(outcome, outcome.name(), delivery);
    }

    static ChangeRoomWeaponResult changeWeapon(
        Room room,
        RoomSubscription subscription,
        List<RoomSubscription> subscriptions,
        long characterId,
        int weaponId
    ) {
        if (room.state() != RoomState.WAITING) {
            return new ChangeRoomWeaponResult(ChangeRoomWeaponOutcome.REJECTED, "ROOM_UNAVAILABLE");
        }

        var id = subscription.membership().sessionId();
        var actor = participant(room, id);

        if (actor.character().id().value() != characterId) {
            return new ChangeRoomWeaponResult(ChangeRoomWeaponOutcome.REJECTED, "CHARACTER_MISMATCH");
        }

        var slot = room.slots().stream().filter(value -> actor.equals(value.participant())).findFirst().orElseThrow();

        if (slot.state() == RoomSlotState.READY) {
            return new ChangeRoomWeaponResult(ChangeRoomWeaponOutcome.REJECTED, "PARTICIPANT_READY");
        }

        if (!Room.isSelectableWeapon(weaponId)) {
            return new ChangeRoomWeaponResult(ChangeRoomWeaponOutcome.REJECTED, "UNSUPPORTED_WEAPON");
        }

        boolean changed = room.changeWeapon(id, weaponId);
        var outcome = changed ? ChangeRoomWeaponOutcome.CHANGED : ChangeRoomWeaponOutcome.UNCHANGED;
        var delivery = RoomNotifications.weaponChanged(
            subscriptions,
            subscription.membership(),
            actor.character().id(),
            weaponId
        );
        RoomNotifications.weaponLog(RoomSnapshot.from(room), id, weaponId, outcome.name());
        return new ChangeRoomWeaponResult(outcome, outcome.name(), delivery);
    }

    private static RoomParticipant participant(Room room, UUID id) {
        return room.slots().stream().map(RoomSlot::participant)
            .filter(value -> value != null && value.sessionId().equals(id)).findFirst().orElseThrow();
    }
}
