package taan.online.character.protocol.packet;

public record CharacterInfo(long characterId, String name, int level, long gold, int gender) {
    public static final int TYPE_ID = 0x22d40519;

    @Override
    public String toString() {
        return "CharacterInfo[REDACTED]";
    }
}
