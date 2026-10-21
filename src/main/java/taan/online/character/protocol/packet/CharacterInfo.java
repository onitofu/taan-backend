package taan.online.character.protocol.packet;

public record CharacterInfo(
    long characterId,
    String name,
    int level,
    long gold,
    int gender,
    long forceMedals,
    long killMedals,
    long supplyMedals,
    long currentExp,
    long levelStartExp,
    long nextLevelExp
) {
    public static final int TYPE_ID = 0x22d40519;

    public CharacterInfo(
        long characterId,
        String name,
        int level,
        long gold,
        int gender,
        long forceMedals,
        long killMedals,
        long supplyMedals
    ) {
        this(characterId, name, level, gold, gender, forceMedals, killMedals, supplyMedals, 0, 0, 0);
    }

    public CharacterInfo(long characterId, String name, int level, long gold, int gender) {
        this(characterId, name, level, gold, gender, 0, 0, 0);
    }

    @Override
    public String toString() {
        return "CharacterInfo[REDACTED]";
    }
}
