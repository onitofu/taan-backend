package taan.online.match.model;

import java.util.Optional;

public final class GhostMovementRules {
    private static final float TWO_PI = 6.283185005187988f;
    private static final float HALF_PI = 1.570796012878418f;

    private GhostMovementRules() {
    }

    public static float speed(Float option1) {
        return option1 != null && option1 > 0 ? 200f + option1 : 200f;
    }

    public static GhostPlan plan(GhostMotionRequest request) {
        float dx = (float) request.target().x() - (float) request.start().x();
        float dy = (float) request.target().y() - (float) request.start().y();
        float squared = dx * dx + dy * dy;

        if (squared < 0.1f) {
            return new GhostPlan(Optional.empty(), request.randomSeed());
        }

        float distance = (float) Math.sqrt(squared);
        float easing = distance <= request.speed() ? distance / request.speed() : 1;
        GhostPoint end = request.target();
        int duration = -(int) (-1500f * easing);

        if (distance > request.speed()) {
            float reciprocal = 1f / distance;
            end = new GhostPoint(
                (int) ((float) request.start().x() + (dx * reciprocal) * request.speed()),
                (int) ((float) request.start().y() + (dy * reciprocal) * request.speed())
            );
            duration = 1500;
        }

        int facing = Integer.compare(request.start().x(), end.x());
        facing = facing == 0 ? request.facing() : facing > 0 ? 1 : 0;
        long seed = request.randomSeed();
        float frequencyX = 0;
        float frequencyY = 0;

        if (duration > 750) {
            seed = nextSeed(seed);
            frequencyX = (seed >>> 17 & 1) + 1;
            seed = nextSeed(seed);
            frequencyY = (seed >>> 17 & 1) + 1;
        }

        var segment = new GhostSegment(
            request.start(),
            end,
            request.startTick(),
            request.startTick() + duration,
            facing,
            easing,
            frequencyX,
            frequencyY
        );
        return new GhostPlan(Optional.of(segment), seed);
    }

    public static GhostPoint nativeSample(GhostSegment segment, long tick) {
        if (tick < segment.startTick()) {
            return segment.start();
        }

        if (tick >= segment.endTick()) {
            return new GhostPoint(0, 0);
        }

        float ratio = (float) (tick - segment.startTick()) / segment.durationMillis();
        float easing = ((float) Math.sin(TWO_PI * ratio) * segment.easing()) * 0.2f;
        float fraction = ratio - easing;
        int x = segment.start().x() + (int) (float) ((double) (segment.end().x() - segment.start().x()) * fraction);
        int y = segment.start().y() + (int) (float) ((double) (segment.end().y() - segment.start().y()) * fraction);
        x += (int) ((float) Math.sin((segment.frequencyX() * fraction) * TWO_PI) * 3f);
        y += (int) ((float) Math.cos((segment.frequencyY() * fraction) * TWO_PI + HALF_PI) * 3f);
        return new GhostPoint(x, y);
    }

    public static GhostPoint positionAt(GhostSegment segment, long tick) {
        return tick >= segment.endTick() ? segment.end() : nativeSample(segment, tick);
    }

    public static long nextSeed(long seed) {
        return (seed * 0x5deece66dL + 11) & 0xffffffffffffL;
    }
}
