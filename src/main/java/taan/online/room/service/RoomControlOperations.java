package taan.online.room.service;

import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.team;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import taan.online.inventory.model.CharacterInventory;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.Room;
import taan.online.room.model.RoomMapCatalog;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomRulePolicy;
import taan.online.room.model.RoomSlot;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomState;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.ChangeRoomSettingsCommand;
import taan.online.room.service.model.ChangeRoomSettingsOutcome;
import taan.online.room.service.model.ChangeRoomSettingsResult;
import taan.online.room.service.model.ChangeRoomTeamOutcome;
import taan.online.room.service.model.ChangeRoomTeamResult;
import taan.online.room.service.model.ChangeRoomWeaponOutcome;
import taan.online.room.service.model.ChangeRoomWeaponResult;
import taan.online.room.service.model.RoomControlRejection;
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
        RoomControlRejection control,
        Room room,
        RoomSubscription subscription,
        List<RoomSubscription> subscriptions,
        int index,
        RoomSlotState desiredState
    ) {
        if (control != null) {
            var outcome = RoomControlChecks.inactive(control) ? SlotAvailabilityOutcome.IGNORED
                : SlotAvailabilityOutcome.REJECTED;
            return new SlotAvailabilityResult(outcome, control.name());
        }

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
        RoomControlRejection control,
        Room room,
        RoomSubscription subscription,
        List<RoomSubscription> subscriptions,
        RoomTeam desiredTeam
    ) {
        if (control != null) {
            var outcome = RoomControlChecks.inactive(control) ? ChangeRoomTeamOutcome.IGNORED
                : ChangeRoomTeamOutcome.REJECTED;
            return new ChangeRoomTeamResult(outcome, control.name());
        }

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
            return new ChangeRoomTeamResult(ChangeRoomTeamOutcome.NO_OPEN_SLOT, "NO_OPEN_TEAM_SLOT");
        }

        var change = changed.orElseThrow();
        var delivery = RoomNotifications.teamChanged(subscriptions, member, change);
        return new ChangeRoomTeamResult(ChangeRoomTeamOutcome.CHANGED, "CHANGED", delivery);
    }

    static SetRoomReadyResult setReady(
        RoomControlRejection control,
        Room room,
        RoomSubscription subscription,
        List<RoomSubscription> subscriptions,
        boolean ready
    ) {
        if (control != null) {
            var outcome = RoomControlChecks.inactive(control) ? SetRoomReadyOutcome.IGNORED
                : SetRoomReadyOutcome.REJECTED;
            return new SetRoomReadyResult(outcome, control.name());
        }

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
        RoomControlRejection control,
        Room room,
        RoomSubscription subscription,
        List<RoomSubscription> subscriptions,
        long characterId,
        int weaponId
    ) {
        if (control != null) {
            var outcome = RoomControlChecks.inactive(control) ? ChangeRoomWeaponOutcome.IGNORED
                : ChangeRoomWeaponOutcome.REJECTED;
            return new ChangeRoomWeaponResult(outcome, control.name());
        }

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
            var delivery = RoomNotifications.deliver(
                subscription,
                () -> subscription.output().weaponChanged(actor.character().id(), actor.weaponId())
            );
            return new ChangeRoomWeaponResult(ChangeRoomWeaponOutcome.REJECTED, "UNSUPPORTED_WEAPON", delivery);
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

    static ChangeRoomSettingsResult publishSettings(
        Room room,
        UUID id,
        RoomMapCatalog maps,
        int kind,
        ChangeRoomSettingsCommand command,
        List<RoomSubscription> subscriptions,
        RoomSubscription subscription,
        Consumer<Room> changed
    ) {
        var result = changeSettings(room, id, maps, command);

        if (result.outcome() == ChangeRoomSettingsOutcome.CHANGED) {
            changed.accept(room);
        }

        return RoomNotifications.settingsChanged(
            subscriptions,
            subscription.membership(),
            RoomSnapshot.from(room),
            result,
            kind,
            command.mapId()
        );
    }

    static ChangeRoomSettingsResult changeSettings(
        Room room,
        UUID id,
        RoomMapCatalog maps,
        ChangeRoomSettingsCommand command
    ) {
        String rejection = null;
        var current = room.settings();
        int mapId = command.mapId();

        if (!room.ownerSessionId().equals(id)) {
            rejection = "NOT_OWNER";
        } else if (room.state() != RoomState.WAITING) {
            rejection = "ROOM_UNAVAILABLE";
        } else if (!maps.contains(mapId)) {
            rejection = "UNSUPPORTED_MAP";
        } else if (!RoomRulePolicy.permits(current.title(), command.mode(), command.rule())) {
            rejection = "UNSUPPORTED_MODE_OR_RULE";
        }

        if (rejection != null) {
            return new ChangeRoomSettingsResult(ChangeRoomSettingsOutcome.REJECTED, rejection, current);
        }

        var mode = command.mode();
        boolean changed = room.changeSettings(mode, RoomRulePolicy.select(current.title(), mode), mapId, maps);
        var outcome = changed ? ChangeRoomSettingsOutcome.CHANGED : ChangeRoomSettingsOutcome.UNCHANGED;
        return new ChangeRoomSettingsResult(outcome, outcome.name(), room.settings());
    }

    static void refreshEquipment(
        Room room,
        RoomSubscription subscription,
        List<RoomSubscription> subscriptions,
        CharacterInventory equipment
    ) {
        try {
            var actor = subscription.membership().sessionId();
            boolean changed = room.updateAppearance(actor, new RoomAppearanceMapper().map(equipment));

            if (changed) {
                RoomNotifications.appearanceUpdated(subscriptions, RoomSnapshot.from(room), actor);
            }
        } catch (RuntimeException failure) {
            RoomNotifications.appearanceFailed(subscription, failure);
        }
    }

    private static RoomParticipant participant(Room room, UUID id) {
        return room.slots().stream().map(RoomSlot::participant)
            .filter(value -> value != null && value.sessionId().equals(id)).findFirst().orElseThrow();
    }
}
