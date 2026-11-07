package taan.online.room.model;

public final class RoomRulePolicy {
    private RoomRulePolicy() {
    }

    public static boolean permits(RoomTitle title, RoomMode mode, RoomRule requested) {
        return mode != null && requested != null && (requested == (mode.usesItems() ? RoomRule.BONUS : RoomRule.BASIC)
            || requested == RoomRule.POWER_QUIZ && select(title, mode) == RoomRule.POWER_QUIZ);
    }

    public static RoomRule select(RoomTitle title, RoomMode mode) {
        return title.value().contains("PowerQuiz") ? RoomRule.POWER_QUIZ
            : mode.usesItems() ? RoomRule.BONUS : RoomRule.BASIC;
    }
}
