package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;

class WeaponDefinitionInvariantTest {
    @Test
    void rejectsMalformedWeaponLocalIdentitiesAndInvalidAttackResourceFields() {
        for (int[] key : new int[][]{{-1, 0}, {12, 0}, {0, -1}, {0, 65536}}) {
            assertThrows(IllegalArgumentException.class, () -> new WeaponMissileKey(key[0], key[1]));
        }

        assertEquals(65535, new WeaponMissileKey(11, 65535).missileTypeId());
        assertThrows(
            NullPointerException.class,
            () -> new WeaponAttackDefinition(
                null,
                0,
                100,
                List.of(new WeaponFireDefinition(0, 0)),
                0,
                false
            )
        );

        for (int animation : new int[]{-1, 256}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new WeaponAttackDefinition(
                    new WeaponAttackKey(0, 0),
                    0,
                    100,
                    List.of(new WeaponFireDefinition(0, 0)),
                    animation,
                    false
                )
            );
        }

        for (int animation : new int[]{0, 8, 15, 255}) {
            var definition = new WeaponAttackDefinition(
                new WeaponAttackKey(0, 0),
                0,
                100,
                List.of(new WeaponFireDefinition(0, 0)),
                animation,
                false
            );
            assertEquals(38, definition.specialAnimation());
        }

        for (int energy : new int[]{-1, 10923}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new WeaponDefinition(0, "cannon", 1000, 100, 2500, energy)
            );
        }

        var mask = MatchFixtures.definitions().missiles().get(new WeaponMissileKey(0, 0)).mask();

        for (int weapon : new int[]{-1, 12}) {
            assertThrows(IllegalArgumentException.class, () -> new MissileTerrainDefinition(weapon, 0, 0, 0, mask));
        }
    }

    @Test
    void validatesCompleteWeaponCatalogsAndRejectsCrossWeaponAliasesEvenWhenAllTwelveRowsArePresent() {
        var original = MatchFixtures.definitions();

        for (int kind = 0; kind < 5; kind++) {
            var attacks = new HashMap<>(original.attacks());
            var missiles = new HashMap<>(original.missiles());
            var key = new WeaponAttackKey(0, 0);

            if (kind == 0) {
                attacks.put(new WeaponAttackKey(1, 0), attacks.remove(key));
            } else if (kind == 1) {
                attacks.remove(key);
            } else if (kind == 2) {
                attacks.put(key, attacks.get(new WeaponAttackKey(8, 0)));
            } else if (kind == 3) {
                missiles.put(new WeaponMissileKey(1, 0), missiles.remove(new WeaponMissileKey(0, 0)));
            } else {
                missiles.put(new WeaponMissileKey(0, 0), missiles.get(new WeaponMissileKey(8, 0)));
            }

            assertThrows(IllegalArgumentException.class, () -> new MatchDefinitions(
                original.catalogVersion(),
                original.map(),
                original.weapons(),
                attacks,
                missiles
            ));
        }
    }
}
