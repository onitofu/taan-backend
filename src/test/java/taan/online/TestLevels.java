package taan.online;

import taan.online.bootstrap.CharacterLevelLoader;
import taan.online.character.model.CharacterLevelTable;

public final class TestLevels {
    private static final CharacterLevelTable TABLE = new CharacterLevelLoader().load(
        TestLevels.class.getResourceAsStream("/character/levels.csv")
    );

    private TestLevels() {
    }

    public static CharacterLevelTable table() {
        return TABLE;
    }
}
