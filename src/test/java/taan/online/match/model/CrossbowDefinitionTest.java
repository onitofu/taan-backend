package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;

class CrossbowDefinitionTest {
    @Test
    void bindsTheSpecialsWireVariantFourToMissileTwoAndLoadsIndependentClientResourceValues() {
        var catalog = MatchFixtures.catalog();
        var weapon = catalog.weapons().get(3);
        assertEquals("crossbow", weapon.asset());
        assertEquals(1000, weapon.resourceMaxHp());
        assertEquals(100, weapon.maxEnergy());
        assertEquals(10, weapon.hitEnergy());
        assertEquals(4000, weapon.resourceMove());
        assertEquals(23, catalog.attacks().size());
        assertEquals(25, catalog.missiles().size());

        for (int kind = 0; kind < 3; kind++) {
            var attack = catalog.attacks().get(new WeaponAttackKey(3, kind));
            assertEquals(0, attack.minPower());
            assertEquals(100, attack.maxPower());
            assertEquals(kind == 2 ? 4 : kind, attack.fires().getFirst().missileVariant());
            assertEquals(new WeaponMissileKey(3, kind), attack.fires().getFirst().missileKey(3));
            assertEquals(kind + 12, attack.animationType());
            assertEquals(40, attack.specialAnimation());
            assertEquals(kind == 2, attack.intrinsicGuidance());
            var mask = catalog.missiles().get(attack.fires().getFirst().missileKey(3));
            assertEquals(128, mask.centerX());
            assertEquals(new int[]{139, 143, 146}[kind], mask.centerY());
        }

        catalog.attacks().values().stream().filter(attack -> attack.key().weaponId() != 3)
            .forEach(attack -> assertFalse(attack.intrinsicGuidance()));
    }
}
