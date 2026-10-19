package taan.online.match.model;

import java.util.List;
import java.util.Objects;

public record MatchDefinitions(String catalogVersion, MatchMapDefinition map, List<WeaponDefinition> weapons) {
    public MatchDefinitions {
        Objects.requireNonNull(catalogVersion);
        Objects.requireNonNull(map);
        weapons = List.copyOf(weapons);

        if (!catalogVersion.matches("[0-9a-f]{64}") || weapons.size() != 12) {
            throw new IllegalArgumentException("Incomplete match reference definitions");
        }

        for (int index = 0; index < map.spawns().size(); index++) {
            if (map.spawns().get(index).index() != index) {
                throw new IllegalArgumentException("Invalid map spawn order");
            }
        }

        for (int index = 0; index < 12; index++) {
            if (weapons.get(index).weaponId() != index) {
                throw new IllegalArgumentException("Invalid match weapon order");
            }
        }
    }
}
