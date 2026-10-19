package taan.online.match.model;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

final class MatchTerrainSupport {
    private static final int RADIUS = 13;

    private MatchTerrainSupport() {
    }

    static int settledY(List<TerrainLayerDefinition> terrain, int x, int y, int mapHeight) {
        return supportY(terrain, x, y, mapHeight).orElse(y);
    }

    static OptionalInt supportY(List<TerrainLayerDefinition> terrain, int x, int y, int mapHeight) {
        if (y > mapHeight) {
            return OptionalInt.empty();
        }

        int central = below(terrain, x, y, mapHeight);

        if (central < 0) {
            return OptionalInt.empty();
        }

        int[] heights = new int[RADIUS * 2 + 1];

        for (int index = 0; index < heights.length; index++) {
            heights[index] = index == RADIUS ? central : below(
                terrain,
                x - RADIUS + index,
                central - RADIUS,
                mapHeight
            );
        }

        return OptionalInt.of(fit(heights));
    }

    private static int below(List<TerrainLayerDefinition> terrain, int x, int y, int height) {
        int nearest = -1;

        for (var layer : terrain) {
            if (x < layer.left() || x >= (long) layer.left() + layer.width()
                || y >= (long) layer.top() + layer.height() || (long) y + height < layer.top()) {
                continue;
            }

            int end = (int) Math.min((long) y + height, (long) layer.top() + layer.height() - 1);

            for (int row = Math.max(y, layer.top()); row <= end; row++) {
                int value = layer.value(x, row);

                if (value != 0 && (value & 0x10) == 0) {
                    nearest = nearest < 0 ? row : Math.min(nearest, row);
                    break;
                }
            }
        }

        return nearest;
    }

    private static int fit(int[] heights) {
        boolean[] connected = connected(heights);
        int left = RADIUS;
        int right = RADIUS;

        for (int index = 0; index < RADIUS; index++) {
            if (connected[index] && heights[index] != -1 && heights[index] < heights[left]) {
                left = index;
            }
        }

        for (int index = heights.length - 1; index > RADIUS; index--) {
            if (connected[index] && heights[index] != -1 && heights[index] < heights[right]) {
                right = index;
            }
        }

        int iterations = 0;

        while (true) {
            int[] line = line(heights, left, right);
            int newLeft = left;
            int newRight = right;
            boolean changed = false;

            for (int index = 0; index < heights.length; index++) {
                if (!connected[index] || heights[index] == -1 || heights[index] >= line[index]) {
                    continue;
                }

                if (index <= RADIUS) {
                    newLeft = index;
                    changed = true;
                }

                if (index >= RADIUS) {
                    newRight = index;
                    changed = true;
                }
            }

            iterations++;

            if (!changed || iterations > 10) {
                return line[RADIUS];
            }

            left = newLeft;
            right = newRight;
        }
    }

    private static boolean[] connected(int[] heights) {
        boolean[] result = new boolean[heights.length];
        result[RADIUS] = true;
        int previous = heights[RADIUS];

        for (int index = RADIUS - 1; index >= 0; index--) {
            if (Math.abs(heights[index] - previous) > RADIUS) {
                break;
            }

            result[index] = true;
            previous = heights[index];
        }

        previous = heights[RADIUS];

        for (int index = RADIUS + 1; index < heights.length; index++) {
            if (Math.abs(heights[index] - previous) > RADIUS) {
                break;
            }

            result[index] = true;
            previous = heights[index];
        }

        return result;
    }

    private static int[] line(int[] heights, int left, int right) {
        int[] result = new int[heights.length];

        if (left == right) {
            Arrays.fill(result, heights[left]);
            return result;
        }

        float slope = (float) ((heights[right] - heights[left]) / (double) (right - left));

        for (int index = 0; index < heights.length; index++) {
            result[index] = heights[left] + (int) ((index - left) * slope - 0.5f);
        }

        return result;
    }
}
