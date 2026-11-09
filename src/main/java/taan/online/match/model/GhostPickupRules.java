package taan.online.match.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

public final class GhostPickupRules {
    private GhostPickupRules() {
    }

    public static List<GhostPickupClaim> plan(MatchGhostState ghost, List<MatchItemBox> boxes) {
        if (ghost.segment().isEmpty()) {
            return List.of();
        }

        var active = boxes.stream().filter(MatchItemBox::active).toList();

        if (active.isEmpty()) {
            return List.of();
        }

        int extent = MatchItemBox.RADIUS + 3;
        int left = active.stream().mapToInt(box -> box.x() - extent).min().orElseThrow();
        int right = active.stream().mapToInt(box -> box.x() + extent).max().orElseThrow();
        int top = active.stream().mapToInt(box -> box.y() - extent).min().orElseThrow();
        int bottom = active.stream().mapToInt(box -> box.y() + extent).max().orElseThrow();
        var segment = ghost.segment().orElseThrow();
        var found = new HashSet<Long>();
        var result = new ArrayList<GhostPickupClaim>();
        long tick = segment.startTick();

        while (true) {
            var point = GhostMovementRules.nativeSample(segment, tick);

            if (point.x() >= left && point.x() < right && point.y() >= top && point.y() < bottom) {
                for (var box : active) {
                    if (!found.contains(box.entityId())
                        && intersects(point, box.x(), box.y(), MatchItemBox.RADIUS_SQUARED, null)) {
                        found.add(box.entityId());
                        result.add(new GhostPickupClaim(box.entityId(), ghost.slotIndex(), tick));
                    }
                }
            }

            if (tick == segment.endTick()) {
                break;
            }

            tick = Math.min(tick + 15, segment.endTick());
        }

        return List.copyOf(result);
    }

    public static boolean intersects(GhostPoint point, int x, int y, int radiusSquared, Float option8) {
        float radius = option8 == null ? 1 : 1f + option8 * 0.01f;
        float factor = radius * radius;
        long dx = (long) x - point.x();
        long dy = (long) y - point.y();
        return dx * dx + dy * dy <= (float) ((double) radiusSquared * factor);
    }

    public static List<GhostPickupClaim> arbitrate(List<GhostPickupClaim> claims) {
        var result = new LinkedHashMap<Long, GhostPickupClaim>();

        for (var claim : claims) {
            var previous = result.get(claim.boxEntityId());

            if (previous == null || claim.tick() < previous.tick()
                || claim.tick() == previous.tick() && claim.slotIndex() < previous.slotIndex()) {
                result.put(claim.boxEntityId(), claim);
            }
        }

        return List.copyOf(result.values());
    }
}
