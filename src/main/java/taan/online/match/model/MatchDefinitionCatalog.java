package taan.online.match.model;

import java.util.List;
import java.util.Map;

public record MatchDefinitionCatalog(
    String catalogVersion,
    Map<Integer, MatchMapDefinition> maps,
    List<WeaponDefinition> weapons,
    CannonShotDefinition cannonShot,
    Map<Integer, MissileTerrainDefinition> missiles
) {
    public MatchDefinitionCatalog {
        maps = Map.copyOf(maps);
        weapons = List.copyOf(weapons);
        missiles = Map.copyOf(missiles);

        if (maps.isEmpty() || maps.entrySet().stream().anyMatch(entry -> entry.getKey() != entry.getValue().mapId())) {
            throw new IllegalArgumentException("Invalid match map catalog");
        }

        for (var map : maps.values()) {
            new MatchDefinitions(catalogVersion, map, weapons, cannonShot, missiles);
        }
    }

    public MatchDefinitions select(int mapId) {
        return new MatchDefinitions(catalogVersion, maps.get(mapId), weapons, cannonShot, missiles);
    }
}
