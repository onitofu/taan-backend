package taan.online.room.service.model;

public record CreateRoomResult(CreateRoomOutcome outcome, RoomSnapshot room, String reason) {
}
