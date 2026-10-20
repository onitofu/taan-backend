package taan.online.match.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record MatchDefinitions(
    String catalogVersion,
    MatchMapDefinition map,
    List<WeaponDefinition> weapons,
    Map<WeaponAttackKey, WeaponAttackDefinition> attacks,
    Map<WeaponMissileKey, MissileTerrainDefinition> missiles
) {
    public MatchDefinitions {
        Objects.requireNonNull(catalogVersion);
        Objects.requireNonNull(map);
        attacks = Map.copyOf(attacks);
        weapons = List.copyOf(weapons);
        missiles = Map.copyOf(missiles);

        var terrain = missiles;
        var supported = attacks.keySet().stream().map(WeaponAttackKey::weaponId).collect(Collectors.toSet());

        if (!supported.equals(Set.of(0, 3, 8, 10)) || attacks.size() != 12 || missiles.size() != 12
            || attacks.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue().key())
                || !terrain.containsKey(e.getValue().missileKey()))
            || missiles.entrySet().stream().anyMatch(e -> !e.getKey().equals(
                new WeaponMissileKey(e.getValue().weaponId(), e.getValue().missileTypeId())))) {
            throw new IllegalArgumentException("Missing or inconsistent weapon attack and terrain definitions");
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
