package taan.online.match.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record MatchDefinitions(
    MatchMapDefinition map,
    Map<Integer, WeaponDefinition> weapons,
    Map<WeaponAttackKey, WeaponAttackDefinition> attacks,
    Map<WeaponMissileKey, MissileTerrainDefinition> missiles
) {
    public MatchDefinitions {
        Objects.requireNonNull(map);
        attacks = Map.copyOf(attacks);
        weapons = Map.copyOf(weapons);
        missiles = Map.copyOf(missiles);

        var terrain = missiles;
        var supported = attacks.keySet().stream().map(WeaponAttackKey::weaponId).collect(Collectors.toSet());

        if (!supported.equals(Set.of(0, 1, 2, 3, 6, 8, 10, 100)) || attacks.size() != 23 || missiles.size() != 25
            || attacks.values().stream().mapToInt(attack -> attack.fires().size()).sum() != 41
            || attacks.containsKey(new WeaponAttackKey(100, 2))
            || attacks.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue().key())
                || e.getValue().fires().stream().anyMatch(
                    fire -> !terrain.containsKey(fire.missileKey(e.getKey().weaponId()))
                ))
            || missiles.entrySet().stream().anyMatch(e -> !e.getKey().equals(
                new WeaponMissileKey(e.getValue().weaponId(), e.getValue().missileTypeId())))) {
            throw new IllegalArgumentException("Missing or inconsistent weapon attack and terrain definitions");
        }

        if (!weapons.keySet().equals(supported)) {
            throw new IllegalArgumentException("Incomplete match reference definitions");
        }

        for (int index = 0; index < map.spawns().size(); index++) {
            if (map.spawns().get(index).index() != index) {
                throw new IllegalArgumentException("Invalid map spawn order");
            }
        }

        for (var entry : weapons.entrySet()) {
            if (entry.getValue().weaponId() != entry.getKey()) {
                throw new IllegalArgumentException("Invalid match weapon identity");
            }
        }
    }
}
