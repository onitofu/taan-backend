package taan.online.room.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.mapId;
import static taan.online.logging.LogField.outcome;
import static taan.online.logging.LogField.previousSlotIndex;
import static taan.online.logging.LogField.ready;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.recipientSessionId;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.roomMode;
import static taan.online.logging.LogField.roomRule;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;
import static taan.online.logging.LogField.team;
import static taan.online.logging.LogField.weaponId;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterId;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomTeamChange;
import taan.online.room.service.model.ChangeRoomSettingsOutcome;
import taan.online.room.service.model.ChangeRoomSettingsResult;
import taan.online.room.service.model.RoomMembership;
import taan.online.room.service.model.RoomSnapshot;

final class RoomNotifications {
    private static final EventLogger LOG = EventLogger.forClass(RoomNotifications.class);

    private RoomNotifications() {
    }

    static CompletionStage<Void> deliver(RoomSubscription recipient, Supplier<CompletionStage<Void>> sending) {
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

    static void update(
        List<RoomSubscription> subscriptions,
        RoomSnapshot snapshot,
        UUID excluded,
        int changedSlot,
        boolean ownerChanged
    ) {
        var recipients = subscriptions.stream().filter(value -> value.membership().roomId().equals(snapshot.id()))
            .filter(value -> !value.membership().sessionId().equals(excluded)).toList();

        for (var recipient : recipients) {
            deliver(recipient, () -> recipient.output().update(snapshot, List.of(changedSlot), ownerChanged));
        }
    }

    static CompletionStage<Void> slotAvailability(
        List<RoomSubscription> subscriptions,
        RoomMembership sender,
        int index,
        RoomSlotState desiredState,
        boolean changed
    ) {
        var recipients = subscriptions.stream().filter(value -> value.membership().roomId().equals(sender.roomId()))
            .filter(value -> changed || value.membership().equals(sender)).toList();
        return broadcast(recipients, sender.sessionId(), output -> output.slotAvailability(index, desiredState));
    }

    static CompletionStage<Void> teamChanged(
        List<RoomSubscription> subscriptions,
        RoomMembership sender,
        RoomTeamChange change
    ) {
        var actor = change.source().participant();
        LOG.log(
            RoomLogEvent.TEAM_CHANGED,
            sessionId(sender.sessionId()),
            accountId(actor.character().accountId().value()),
            characterId(actor.character().id().value()),
            channelId(sender.channelId()),
            roomId(sender.roomId().value()),
            previousSlotIndex(change.source().index()),
            slotIndex(change.destination().index()),
            team(change.destination().team().name())
        );
        var recipients = subscriptions.stream().filter(value -> value.membership().roomId().equals(sender.roomId()))
            .toList();
        return broadcast(recipients, sender.sessionId(), output -> output.teamChanged(change));
    }

    static CompletionStage<Void> readyChanged(
        List<RoomSubscription> subscriptions,
        RoomMembership sender,
        AccountId account,
        boolean ready
    ) {
        var recipients = subscriptions.stream().filter(value -> value.membership().roomId().equals(sender.roomId()))
            .toList();
        return broadcast(recipients, sender.sessionId(), output -> output.readyChanged(account, ready));
    }

    static ChangeRoomSettingsResult settingsChanged(
        List<RoomSubscription> subscriptions,
        RoomMembership sender,
        RoomSnapshot snapshot,
        ChangeRoomSettingsResult result
    ) {
        boolean rejected = result.outcome() == ChangeRoomSettingsOutcome.REJECTED;
        var recipients = subscriptions.stream().filter(value -> value.membership().roomId().equals(sender.roomId()))
            .filter(value -> !rejected || value.membership().equals(sender)).toList();
        var delivery = broadcast(
            recipients,
            sender.sessionId(),
            output -> output.settingsChanged(result.settings(), rejected)
        );
        var actor = snapshot.slots().stream().map(value -> value.participant())
            .filter(value -> value != null && value.sessionId().equals(sender.sessionId())).findFirst().orElseThrow();
        var settings = result.settings();
        LOG.log(
            rejected ? RoomLogEvent.SETTINGS_REJECTED : RoomLogEvent.SETTINGS_SET,
            sessionId(sender.sessionId()),
            accountId(actor.character().accountId().value()),
            characterId(actor.character().id().value()),
            channelId(sender.channelId()),
            roomId(sender.roomId().value()),
            mapId(settings.mapId()),
            roomMode(settings.mode().name()),
            roomRule(settings.rule().name()),
            outcome(result.outcome().name()),
            reason(result.reason())
        );
        return new ChangeRoomSettingsResult(result.outcome(), result.reason(), settings, delivery);
    }

    static CompletionStage<Void> weaponChanged(
        List<RoomSubscription> subscriptions,
        RoomMembership sender,
        CharacterId character,
        int weapon
    ) {
        var recipients = subscriptions.stream().filter(value -> value.membership().roomId().equals(sender.roomId()))
            .toList();
        return broadcast(recipients, sender.sessionId(), output -> output.weaponChanged(character, weapon));
    }

    static void weaponLog(RoomSnapshot room, UUID actor, int weapon, String result) {
        var slot = room.slots().stream().filter(value -> value.participant() != null)
            .filter(value -> value.participant().sessionId().equals(actor)).findFirst().orElseThrow();
        LOG.log(
            RoomLogEvent.WEAPON_SELECTED,
            sessionId(actor),
            accountId(slot.participant().character().accountId().value()),
            characterId(slot.participant().character().id().value()),
            channelId(room.channelId()),
            roomId(room.id().value()),
            slotIndex(slot.index()),
            weaponId(weapon),
            outcome(result)
        );
    }

    static void ownerChanged(
        List<RoomSubscription> subscriptions,
        RoomSnapshot previous,
        RoomSnapshot current,
        boolean changed,
        String closeReason
    ) {
        if (!changed) {
            return;
        }

        log(RoomLogEvent.OWNER_CHANGED, current, current.owner(), closeReason);
        boolean wasReady = previous.slots().stream().anyMatch(
            slot -> slot.state() == RoomSlotState.READY
                && slot.participant().sessionId().equals(current.ownerSessionId())
        );

        if (wasReady) {
            var owner = subscriptions.stream()
                .filter(value -> value.membership().sessionId().equals(current.ownerSessionId())).findFirst()
                .orElseThrow();
            readyChanged(subscriptions, owner.membership(), current.owner().character().accountId(), false);
            readyLog(RoomLogEvent.READY_RESET, current, current.ownerSessionId(), false, "CHANGED", "OWNER_CHANGED");
        }
    }

    static void availabilityLog(
        int channel,
        RoomId room,
        RoomParticipant actor,
        int index,
        RoomSlotState desiredState,
        String result
    ) {
        LOG.log(
            desiredState == RoomSlotState.OPEN ? RoomLogEvent.SLOT_OPENED : RoomLogEvent.SLOT_CLOSED,
            sessionId(actor.sessionId()),
            accountId(actor.character().accountId().value()),
            characterId(actor.character().id().value()),
            channelId(channel),
            roomId(room.value()),
            slotIndex(index),
            outcome(result)
        );
    }

    static void readyLog(
        RoomLogEvent event,
        RoomSnapshot room,
        UUID actor,
        boolean value,
        String result,
        String resetReason
    ) {
        var slot = room.slots().stream().filter(item -> item.participant() != null)
            .filter(item -> item.participant().sessionId().equals(actor)).findFirst().orElseThrow();
        LOG.log(
            event,
            sessionId(actor),
            accountId(slot.participant().character().accountId().value()),
            characterId(slot.participant().character().id().value()),
            channelId(room.channelId()),
            roomId(room.id().value()),
            slotIndex(slot.index()),
            ready(value),
            reason(resetReason),
            outcome(result)
        );
    }

    static void log(RoomLogEvent event, RoomSnapshot room, RoomParticipant actor, String closeReason) {
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

    private static CompletionStage<Void> broadcast(
        List<RoomSubscription> recipients,
        UUID sender,
        Function<RoomPresenceOutput, CompletionStage<Void>> sending
    ) {
        CompletionStage<Void> delivery = CompletableFuture.completedFuture(null);

        for (var recipient : recipients) {
            var sent = deliver(recipient, () -> sending.apply(recipient.output()));

            if (recipient.membership().sessionId().equals(sender)) {
                delivery = sent;
            }
        }

        return delivery;
    }
}
