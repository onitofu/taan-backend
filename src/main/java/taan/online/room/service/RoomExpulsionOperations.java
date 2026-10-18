package taan.online.room.service;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.service.model.ExpelRoomParticipantOutcome;
import taan.online.room.service.model.ExpelRoomParticipantResult;
import taan.online.room.service.model.RoomLeaveReceipt;
import taan.online.room.service.model.RoomLeaveStatus;
import taan.online.room.service.model.RoomMembership;
import taan.online.room.service.model.RoomSnapshot;

final class RoomExpulsionOperations {
    private RoomExpulsionOperations() {
    }

    static ExpelRoomParticipantResult expel(
        UUID id,
        long targetCharacterId,
        Map<RoomId, Room> rooms,
        Map<UUID, RoomSubscription> memberships,
        Map<UUID, RoomLeaveReceipt> receipts,
        Function<UUID, String> controlRejection,
        BiConsumer<RoomMembership, String> remove,
        Consumer<Room> beforeRemoval
    ) {
        String rejection = controlRejection.apply(id);

        if (rejection != null) {
            return RoomNotifications.expelRejected(id, rejection);
        }

        var snapshot = RoomSnapshot.from(rooms.get(memberships.get(id).membership().roomId()));
        var slot = snapshot.slots().stream().filter(value -> value.participant() != null)
            .filter(value -> value.participant().character().id().value() == targetCharacterId)
            .findFirst().orElse(null);
        var target = slot == null ? null : slot.participant();
        rejection = RoomControlChecks.expel(rooms.get(snapshot.id()), id, target);

        if (rejection == null) {
            rejection = controlRejection.apply(target.sessionId());
        }

        if (rejection != null) {
            return RoomNotifications.expelRejected(id, rejection);
        }

        var subscription = memberships.get(target.sessionId());
        beforeRemoval.accept(rooms.get(snapshot.id()));
        RoomNotifications.expelled(subscription, target);
        remove.accept(subscription.membership(), "EXPELLED");
        receipts.put(
            target.sessionId(),
            new RoomLeaveReceipt(subscription.membership(), RoomLeaveStatus.AWAITING_ACKNOWLEDGEMENT)
        );
        var actor = snapshot.slots().stream().map(slotValue -> slotValue.participant())
            .filter(value -> value != null && value.sessionId().equals(id)).findFirst().orElseThrow();
        RoomNotifications.expelLog(snapshot, actor, target, slot.index());
        return new ExpelRoomParticipantResult(ExpelRoomParticipantOutcome.EXPELLED, "EXPELLED");
    }

}
