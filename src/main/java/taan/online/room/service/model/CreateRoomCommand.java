package taan.online.room.service.model;

import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;

public record CreateRoomCommand(String title, RoomMode mode, RoomRule rule, String password) {
    @Override
    public String toString() {
        return "CreateRoomCommand[REDACTED]";
    }
}
