package taan.online.match.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record MatchDefinitions(
    String catalogVersion,
    MatchMapDefinition map,
    Map<Integer, WeaponDefinition> weapons,
    Map<WeaponAttackKey, WeaponAttackDefinition> attacks,
    Map<WeaponMissileKey, MissileTerrainDefinition> missiles
) {
    public MatchDefinitions {
        Objects.requireNonNull(catalogVersion);
        Objects.requireNonNull(map);
        attacks = Map.copyOf(attacks);
        weapons = Map.copyOf(weapons);
        missiles = Map.copyOf(missiles);

        var terrain = missiles;
        var supported = attacks.keySet().stream().map(WeaponAttackKey::weaponId).collect(Collectors.toSet());

        if (!supported.equals(Set.of(0, 3, 8, 10, 100)) || attacks.size() != 14 || missiles.size() != 14
            || attacks.containsKey(new WeaponAttackKey(100, 2))
            || attacks.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue().key())
                || !terrain.containsKey(e.getValue().missileKey()))
            || missiles.entrySet().stream().anyMatch(e -> !e.getKey().equals(
                new WeaponMissileKey(e.getValue().weaponId(), e.getValue().missileTypeId())))) {
            throw new IllegalArgumentException("Missing or inconsistent weapon attack and terrain definitions");
        }

        var expectedWeapons = IntStream.range(0, 12).boxed().collect(Collectors.toSet());
        expectedWeapons.add(100);

        if (!catalogVersion.matches("[0-9a-f]{64}") || !weapons.keySet().equals(expectedWeapons)) {
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
