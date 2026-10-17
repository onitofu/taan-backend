package taan.online.room.model;

public final class RoomPassword {
    private final String value;

    public RoomPassword(String value) {
        if (value == null || value.isEmpty() || value.length() > 10) {
            throw new IllegalArgumentException("Invalid room password length");
        }

        if (value.codePoints().anyMatch(point -> point >= 0xd800 && point <= 0xdfff)) {
            throw new IllegalArgumentException("Invalid room password encoding");
        }

        this.value = value;
    }

    public boolean matches(String candidate) {
        return value.equals(candidate);
    }

    @Override
    public String toString() {
        return "RoomPassword[REDACTED]";
    }
}
