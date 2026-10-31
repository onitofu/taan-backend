package taan.online.room.controller;

import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.protocol.packet.GameSessionInfo;
import taan.online.room.service.model.ChangeRoomSettingsCommand;
import taan.online.room.service.model.CreateRoomCommand;

public final class RoomSettingsMapper {
    private RoomSettingsMapper() {
    }

    public static CreateRoomCommand create(GameSessionInfo info) {
        var details = info.details();

        if (info.state() != 5 || details == null || details.roomGrade() != 0 || details.gameKind() != 0
            || details.playerCount() != 0 || details.capacity() != 8 || details.mapId() < 0 || details.mapId() > 1) {
            return null;
        }

        return new CreateRoomCommand(
            details.title(),
            mode(details.gameType()),
            rule(details.rule()),
            details.password()
        );
    }

    public static ChangeRoomSettingsCommand settings(int mapId, int gameType, int rule) {
        return new ChangeRoomSettingsCommand(mapId, mode(gameType), rule(rule));
    }

    private static RoomMode mode(int value) {
        return switch (value) {
            case 0 -> RoomMode.ITEM;
            case 1 -> RoomMode.NO_ITEM;
            case 2 -> RoomMode.CAPTAIN_ITEM;
            case 3 -> RoomMode.CAPTAIN_NO_ITEM;
            default -> null;
        };
    }

    private static RoomRule rule(int value) {
        return switch (value) {
            case 0 -> RoomRule.BASIC;
            case 1 -> RoomRule.POWER_QUIZ;
            case 2 -> RoomRule.BONUS;
            default -> null;
        };
    }
}
