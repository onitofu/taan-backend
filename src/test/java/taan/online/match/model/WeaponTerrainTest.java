package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;

class WeaponTerrainTest {
    @Test
    void appliesTheIndependentSourceRedChannelAreasInsteadOfReusingTheCannonsMask() {
        int[][] areas = {{16520, 18377, 20006}, {6900, 8024, 14004}, {5687, 7848, 9871}};
        int[] weapons = {0, 8, 10};
        var definitions = MatchFixtures.definitions();

        for (int index = 0; index < weapons.length; index++) {
            for (int kind = 0; kind < 3; kind++) {
                var key = new WeaponMissileKey(weapons[index], kind);
                var missile = definitions.missiles().get(key);
                byte[] cells = new byte[512 * 512];
                Arrays.fill(cells, (byte) 1);
                var layer = new TerrainLayerDefinition(-256, -256, 512, 512, cells);
                var draft = new ScenarioTerrainDraft(List.of(layer));

                draft.explode(missile, 0, 0, 1);
                assertEquals(areas[index][kind], draft.changedCells());
                draft.explode(missile, 0, 0, 1);
                assertEquals(areas[index][kind], draft.changedCells());
                assertEquals(1, layer.value(0, 0));
                assertEquals(weapons[index], missile.weaponId());
                assertEquals(kind, missile.missileTypeId());

                if (index > 0) {
                    assertFalse(Arrays.equals(missile.mask().cells(), definitions.missiles()
                        .get(new WeaponMissileKey(0, kind)).mask().cells()));
                }
            }
        }
    }
}
