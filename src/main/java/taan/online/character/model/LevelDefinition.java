package taan.online.character.model;

public record LevelDefinition(int level, long totalExperience) {
    public LevelDefinition {
        if (level < 1 || level > 109 || totalExperience < 0 || totalExperience > 1048576) {
            throw new IllegalArgumentException("Invalid level definition");
        }
    }
}
