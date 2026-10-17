package taan.online.room.model;

public record RoomTitle(String value) {
    public RoomTitle {
        if (value == null || value.isEmpty() || value.length() > 256) {
            throw new IllegalArgumentException("Invalid room title length");
        }

        if (value.codePoints().allMatch(point -> Character.isWhitespace(point) || Character.isSpaceChar(point))) {
            throw new IllegalArgumentException("Room title must not be blank");
        }

        if (value.codePoints().anyMatch(
            point -> Character.isISOControl(point) || point == 0x2028 || point == 0x2029
                || point >= 0xd800 && point <= 0xdfff
        )) {
            throw new IllegalArgumentException("Unsupported room title symbol");
        }
    }

    @Override
    public String toString() {
        return "RoomTitle[REDACTED]";
    }
}
