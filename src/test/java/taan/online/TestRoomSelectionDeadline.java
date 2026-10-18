package taan.online;

import taan.online.room.service.model.RoomSelectionToken;

public record TestRoomSelectionDeadline(RoomSelectionToken token, long due, Runnable command) {
}
