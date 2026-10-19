package taan.online.match.model;

import java.util.List;
import java.util.Map;

public record MatchDefinitionCatalog(
    String catalogVersion,
    Map<Integer, MatchMapDefinition> maps,
    List<WeaponDefinition> weapons,
    Map<Integer, CannonShotDefinition> cannonShots,
    Map<Integer, MissileTerrainDefinition> missiles
) {
    public MatchDefinitionCatalog {
        maps = Map.copyOf(maps);
        weapons = List.copyOf(weapons);
        missiles = Map.copyOf(missiles);
        cannonShots = Map.copyOf(cannonShots);

        if (maps.isEmpty() || maps.entrySet().stream().anyMatch(entry -> entry.getKey() != entry.getValue().mapId())) {
            throw new IllegalArgumentException("Invalid match map catalog");
        }

        for (var map : maps.values()) {
            new MatchDefinitions(catalogVersion, map, weapons, cannonShots, missiles);
        }
    }

    public MatchDefinitions select(int mapId) {
        return new MatchDefinitions(catalogVersion, maps.get(mapId), weapons, cannonShots, missiles);
    }
}
