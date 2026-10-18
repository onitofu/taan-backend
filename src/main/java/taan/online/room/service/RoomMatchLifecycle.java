package taan.online.room.service;

import taan.online.room.service.model.RoomMatchDraft;
import taan.online.room.service.model.RoomMatchReservationResult;
import taan.online.room.service.model.RoomMatchToken;

public interface RoomMatchLifecycle {
    RoomMatchReservationResult reserve(RoomMatchDraft draft);

    RoomMatchReservationResult commit(RoomMatchToken token);

    RoomMatchReservationResult cancel(RoomMatchToken token, String reason);
}
