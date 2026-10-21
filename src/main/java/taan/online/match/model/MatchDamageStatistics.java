package taan.online.match.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record MatchDamageStatistics(Map<UUID, Long> damage) {
    public MatchDamageStatistics {
        damage = Map.copyOf(damage);
        damage.values().forEach(MatchDamageStatistics::requireDamage);
    }

    public static MatchDamageStatistics empty() {
        return new MatchDamageStatistics(Map.of());
    }

    public long damage(UUID session) {
        return damage.getOrDefault(session, 0L);
    }

    public MatchDamageStatistics credit(UUID session, long amount) {
        requireDamage(amount);

        if (amount == 0) {
            return this;
        }

        long total = Math.addExact(damage(session), amount);
        requireDamage(total);
        var updated = new HashMap<>(damage);
        updated.put(session, total);
        return new MatchDamageStatistics(updated);
    }

    private static void requireDamage(long value) {
        if (value < 0 || value > 0xffffffffL) {
            throw new IllegalArgumentException("Match damage is outside the unsigned integer range");
        }
    }
}
