package taan.online.room.view;

import taan.online.room.model.RoomRule;

public final class RoomRuleMapper {
    public int map(RoomRule rule) {
        return switch (rule) {
            case BASIC -> 0;
            case POWER_QUIZ -> 1;
            case BONUS -> 2;
        };
    }
}
