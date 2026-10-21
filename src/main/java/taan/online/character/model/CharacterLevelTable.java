package taan.online.character.model;

import java.util.List;

public final class CharacterLevelTable {
    public static final long EXPERIENCE_CAP = 1048576;

    private final List<LevelDefinition> levels;

    public CharacterLevelTable(List<LevelDefinition> levels) {
        this.levels = List.copyOf(levels);

        if (levels.size() != 109 || levels.getFirst().totalExperience() != 0
            || levels.getLast().totalExperience() != EXPERIENCE_CAP) {
            throw new IllegalArgumentException("Incomplete level table");
        }

        for (int index = 0; index < levels.size(); index++) {
            var definition = levels.get(index);

            if (definition.level() != index + 1
                || index > 0 && definition.totalExperience() <= levels.get(index - 1).totalExperience()) {
                throw new IllegalArgumentException("Unordered level table");
            }
        }
    }

    public int level(long experience) {
        if (experience < 0 || experience > EXPERIENCE_CAP) {
            throw new IllegalArgumentException("Experience is outside the progression range");
        }

        int level = 1;

        while (level < levels.size() && experience >= levels.get(level).totalExperience()) {
            level++;
        }

        return level;
    }

    public long threshold(int level) {
        if (level < 1 || level > levels.size()) {
            throw new IllegalArgumentException("Level is outside the progression range");
        }

        return levels.get(level - 1).totalExperience();
    }

    public long displayStart(int level) {
        return threshold(level == 109 ? 108 : level);
    }

    public long displayNext(int level) {
        return threshold(Math.min(level + 1, 109));
    }
}
