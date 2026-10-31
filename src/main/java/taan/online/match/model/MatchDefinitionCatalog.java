package taan.online.match.model;

import java.util.Map;

public record MatchDefinitionCatalog(
    Map<Integer, MatchMapDefinition> maps,
    Map<Integer, WeaponDefinition> weapons,
    Map<WeaponAttackKey, WeaponAttackDefinition> attacks,
    Map<WeaponMissileKey, MissileTerrainDefinition> missiles
) {
    public MatchDefinitionCatalog {
        maps = Map.copyOf(maps);
        weapons = Map.copyOf(weapons);
        missiles = Map.copyOf(missiles);
        attacks = Map.copyOf(attacks);

        if (maps.isEmpty() || maps.entrySet().stream().anyMatch(entry -> entry.getKey() != entry.getValue().mapId())) {
            throw new IllegalArgumentException("Invalid match map catalog");
        }

        for (var map : maps.values()) {
            new MatchDefinitions(map, weapons, attacks, missiles);
        }
    }

    public MatchDefinitions select(int mapId) {
        return new MatchDefinitions(maps.get(mapId), weapons, attacks, missiles);
    }
}
