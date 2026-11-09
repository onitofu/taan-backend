package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class GhostMovementRulesTest {
    @Test
    void originalX86Snapshots() throws Exception {
        int count = 0;

        try (var reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("/match/ghost/ghost-native.tsv"),
            StandardCharsets.UTF_8
        ))) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] pair = line.split("\\t");
                var input = ByteBuffer.wrap(HexFormat.of().parseHex(pair[0])).order(ByteOrder.LITTLE_ENDIAN);
                var expected = ByteBuffer.wrap(HexFormat.of().parseHex(pair[1])).order(ByteOrder.LITTLE_ENDIAN);
                input.getInt();
                var start = new GhostPoint(input.getInt(), input.getInt());
                var target = new GhostPoint(input.getInt(), input.getInt());
                float speed = input.getFloat();
                long tick = Integer.toUnsignedLong(input.getInt());
                long sampleTick = Integer.toUnsignedLong(input.getInt());
                long seed = input.getLong();
                int facing = input.getInt();
                var plan = GhostMovementRules.plan(new GhostMotionRequest(start, target, facing, speed, tick, seed));
                String context = "snapshot " + count;
                if (plan.segment().isPresent()) {
                    var segment = plan.segment().orElseThrow();
                    assertEquals(new GhostPoint(expected.getInt(), expected.getInt()), segment.start(), context);
                    assertEquals(Integer.toUnsignedLong(expected.getInt()), segment.startTick(), context);
                    assertEquals(new GhostPoint(expected.getInt(), expected.getInt()), segment.end(), context);
                    assertEquals(Integer.toUnsignedLong(expected.getInt()), segment.endTick(), context);
                    assertEquals(expected.getInt(), segment.facing(), context);
                    assertEquals(expected.getFloat(), segment.easing(), context);
                    assertEquals(expected.getFloat(), segment.frequencyX(), context);
                    assertEquals(expected.getFloat(), segment.frequencyY(), context);
                    assertEquals(
                        new GhostPoint(expected.getInt(), expected.getInt()),
                        GhostMovementRules.nativeSample(segment, sampleTick),
                        context
                    );
                } else {
                    assertEquals(start, target, context);
                    expected.position(48);
                }
                assertEquals(expected.getLong(), plan.randomSeed(), context);
                count++;
            }
        }
        assertEquals(600, count);
    }

    @Test
    void handlesSpeedAndCompletedPositions() {
        assertEquals(200, GhostMovementRules.speed(null));
        assertEquals(200, GhostMovementRules.speed(-10f));
        assertEquals(200, GhostMovementRules.speed(0f));
        assertEquals(250, GhostMovementRules.speed(50f));
        var segment = GhostMovementRules.plan(new GhostMotionRequest(
            new GhostPoint(100, 100),
            new GhostPoint(300, 100),
            0,
            200,
            1000,
            0
        )).segment().orElseThrow();
        assertEquals(segment.start(), GhostMovementRules.positionAt(segment, 999));
        assertEquals(segment.end(), GhostMovementRules.positionAt(segment, 2500));
        assertEquals(segment.end(), GhostMovementRules.positionAt(segment, 3000));
        assertEquals(new GhostPoint(0, 0), GhostMovementRules.nativeSample(segment, 2500));
    }
}
