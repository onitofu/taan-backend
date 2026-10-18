package taan.online.room.view;

import taan.online.room.model.RoomMode;

public final class RoomModeMapper {
    public int map(RoomMode mode) {
        return switch (mode) {
            case ITEM -> 0;
            case NO_ITEM -> 1;
            case CAPTAIN_ITEM -> 2;
            case CAPTAIN_NO_ITEM -> 3;
        };
    }
}
