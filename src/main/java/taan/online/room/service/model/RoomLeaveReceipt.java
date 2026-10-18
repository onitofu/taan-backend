package taan.online.room.service.model;

public record RoomLeaveReceipt(RoomMembership membership, RoomLeaveStatus status) {
    public RoomLeaveReceipt(RoomMembership membership) {
        this(membership, RoomLeaveStatus.COMPLETED);
    }

    public boolean awaitingAcknowledgement() {
        return status == RoomLeaveStatus.AWAITING_ACKNOWLEDGEMENT;
    }
}
