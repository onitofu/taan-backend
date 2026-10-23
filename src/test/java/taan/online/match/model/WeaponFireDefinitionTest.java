package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeaponFireDefinitionTest {
    @Test
    void freezesOrderedProfilesAndRejectsEmptyOrAmbiguousVariantMappings() {
        var first = new WeaponFireDefinition(255, 65535);
        assertEquals(new WeaponMissileKey(100, 65535), first.missileKey(100));
        var profiles = new ArrayList<>(List.of(first, new WeaponFireDefinition(0, 0)));
        var attack = definition(profiles);
        profiles.clear();
        assertEquals(List.of(first, new WeaponFireDefinition(0, 0)), attack.fires());
        assertThrows(UnsupportedOperationException.class, () -> attack.fires().clear());
        assertThrows(IllegalArgumentException.class, () -> definition(List.of()));
        assertThrows(IllegalArgumentException.class, () -> definition(List.of(first, first)));
        assertThrows(
            IllegalArgumentException.class,
            () -> definition(List.of(first, new WeaponFireDefinition(255, 0)))
        );
        assertThrows(NullPointerException.class, () -> definition(null));
        assertThrows(NullPointerException.class, () -> definition(Arrays.asList(first, null)));
    }

    private WeaponAttackDefinition definition(List<WeaponFireDefinition> fires) {
        return new WeaponAttackDefinition(new WeaponAttackKey(1, 2), 0, 100, fires, 8, false);
    }
}
