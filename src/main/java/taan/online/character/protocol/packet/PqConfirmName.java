package taan.online.character.protocol.packet;

public record PqConfirmName(int localSlot, String name) {
    public static final int TYPE_ID = 0x22e70510;

    @Override
    public String toString() {
        return "PqConfirmName[REDACTED]";
    }
}
