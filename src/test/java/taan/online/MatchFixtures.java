package taan.online;

import taan.online.bootstrap.MatchDefinitionLoader;
import taan.online.match.model.MatchDefinitions;

public final class MatchFixtures {
    private MatchFixtures() {
    }

    public static MatchDefinitions definitions() {
        return new MatchDefinitionLoader().load(
            MatchFixtures.class.getResourceAsStream("/match/jungle.properties"),
            MatchFixtures.class.getResourceAsStream("/match/jungle-spawns.csv"),
            MatchFixtures.class.getResourceAsStream("/match/weapons.csv")
        );
    }
}
