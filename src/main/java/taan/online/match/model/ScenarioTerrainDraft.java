package taan.online.match.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ScenarioTerrainDraft {
    private final List<TerrainLayerDefinition> original;
    private final Map<Integer, byte[]> modified = new HashMap<>();
    private int changedCells;

    ScenarioTerrainDraft(List<TerrainLayerDefinition> original) {
        this.original = List.copyOf(original);
    }

    void explode(MissileTerrainDefinition definition, int x, int y, float factor) {
        if (!Float.isFinite(factor) || factor < 0 || factor > 1) {
            throw new ScenarioWorldException("INVALID_TERRAIN_FACTOR");
        }

        var mask = definition.mask();
        int left = coordinate((long) x - definition.centerX());
        int top = coordinate((long) y - (int) (definition.centerY() * factor));
        int right = coordinate((long) left + mask.width());
        int bottom = coordinate((long) top + (int) (mask.height() * factor));

        if (bottom == top) {
            return;
        }

        for (int index = 0; index < original.size(); index++) {
            var layer = original.get(index);
            int fromX = Math.max(left, layer.left());
            int toX = Math.min(right, layer.left() + layer.width());
            int fromY = Math.max(top, layer.top());
            int toY = Math.min(bottom, layer.top() + layer.height());

            for (int row = fromY; row < toY; row++) {
                int sourceY = (int) ((row - top) * (1.0f / factor));

                for (int column = fromX; column < toX; column++) {
                    if (mask.value(column - left, sourceY) == 1) {
                        remove(index, layer, column, row);
                    }
                }
            }
        }
    }

    private int coordinate(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new ScenarioWorldException("TERRAIN_COORDINATE_OVERFLOW");
        }

        return (int) value;
    }

    private void remove(int index, TerrainLayerDefinition layer, int x, int y) {
        int offset = (y - layer.top()) * layer.width() + x - layer.left();
        byte[] cells = modified.get(index);
        int value = cells == null ? layer.value(x, y) : Byte.toUnsignedInt(cells[offset]);

        if (value != 1) {
            return;
        }

        if (cells == null) {
            cells = layer.cells();
            modified.put(index, cells);
        }

        cells[offset] = 0x11;
        changedCells++;
    }

    int changedCells() {
        return changedCells;
    }

    List<TerrainLayerDefinition> freeze() {
        var layers = new ArrayList<>(original);
        modified.forEach((index, cells) -> layers.set(index, original.get(index).replace(cells)));
        return List.copyOf(layers);
    }
}
