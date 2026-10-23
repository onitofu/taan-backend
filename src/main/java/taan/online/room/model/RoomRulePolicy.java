package taan.online.room.model;

public final class RoomRulePolicy {
    private RoomRulePolicy() {
    }

    public static RoomRule select(RoomTitle title, RoomMode mode) {
        if (!mode.usesItems()) {
            return RoomRule.BASIC;
        }

        return title.value().contains("PowerQuiz") ? RoomRule.POWER_QUIZ : RoomRule.BONUS;
    }
}
