package taan.online.room.service.model;

public record RoomMatchReservationResult(RoomMatchReservationOutcome outcome, String reason, RoomMatchToken token) {
}
