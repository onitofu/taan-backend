package taan.online.match.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record MatchDefinitions(
    String catalogVersion,
    MatchMapDefinition map,
    List<WeaponDefinition> weapons,
    Map<Integer, CannonShotDefinition> cannonShots,
    Map<Integer, MissileTerrainDefinition> missiles
) {
    public MatchDefinitions {
        Objects.requireNonNull(catalogVersion);
        Objects.requireNonNull(map);
        cannonShots = Map.copyOf(cannonShots);
        weapons = List.copyOf(weapons);
        missiles = Map.copyOf(missiles);

        var terrain = missiles;

        if (!cannonShots.keySet().equals(Set.of(0, 1, 2))
            || cannonShots.entrySet().stream().anyMatch(e -> e.getKey() != e.getValue().attackKind()
                || !terrain.containsKey(e.getValue().missileTypeId()))
            || missiles.entrySet().stream().anyMatch(entry -> entry.getKey() != entry.getValue().missileTypeId())) {
            throw new IllegalArgumentException("Missing or inconsistent missile terrain definitions");
        }

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
