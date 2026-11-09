package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class GhostPickupNativeTest {
    @Test
    void matchesAllOriginalX86PickupSamples() throws Exception {
        int count = 0;

        try (var reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("/match/ghost/ghost-pickup-native.tsv"),
            StandardCharsets.UTF_8
        ))) {
            for (String line; (line = reader.readLine()) != null;) {
                var pair = line.split("\\t");
                var input = ByteBuffer.wrap(HexFormat.of().parseHex(pair[0])).order(ByteOrder.LITTLE_ENDIAN);
                var expected = ByteBuffer.wrap(HexFormat.of().parseHex(pair[1])).order(ByteOrder.LITTLE_ENDIAN);
                input.getInt();
                var start = new GhostPoint(input.getInt(), input.getInt());
                var target = new GhostPoint(input.getInt(), input.getInt());
                float speed = input.getFloat();
                long tick = Integer.toUnsignedLong(input.getInt());
                input.getInt();
                long seed = input.getLong();
                int facing = input.getInt();
                var segment = GhostMovementRules.plan(new GhostMotionRequest(
                    start,
                    target,
                    facing,
                    speed,
                    tick,
                    seed
                )).segment().orElseThrow();
                int x = input.getInt();
                int y = input.getInt();
                int radius = input.getInt();
                float option = input.getFloat();
                boolean present = input.getInt() != 0;
                long found = -1;

                for (long sample = segment.startTick();;) {
                    var point = GhostMovementRules.nativeSample(segment, sample);

                    if (point.x() >= -4096 && point.x() < 4096 && point.y() >= -4096 && point.y() < 4096
                        && GhostPickupRules.intersects(point, x, y, radius, present ? option : null)) {
                        found = sample;
                        break;
                    }

                    if (sample == segment.endTick()) {
                        break;
                    }

                    sample = Math.min(sample + 15, segment.endTick());
                }

                int size = expected.getInt();
                assertEquals(size, found >= 0 ? 1 : 0, "sample " + count);

                if (size > 0) {
                    assertEquals(Integer.toUnsignedLong(expected.getInt()), found, "sample " + count);
                }

                count++;
            }
        }

        assertEquals(180, count);
    }
}
