package taan.online.character.model;

public record CharacterProgress(long experience, long forceMedals, long killMedals, long supplyMedals) {
    public CharacterProgress {
        requireCounter(experience);
        requireCounter(forceMedals);
        requireCounter(killMedals);
        requireCounter(supplyMedals);
    }

    public static CharacterProgress initial() {
        return new CharacterProgress(0, 0, 0, 0);
    }

    private static void requireCounter(long value) {
        if (value < 0 || value > 0xffffffffL) {
            throw new IllegalArgumentException("Character progress is outside the unsigned integer range");
        }
    }
}
