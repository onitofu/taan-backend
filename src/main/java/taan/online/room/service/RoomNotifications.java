package taan.online.room.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.previousSlotIndex;
import static taan.online.logging.LogField.recipientSessionId;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;
import static taan.online.logging.LogField.team;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomTeamChange;
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
