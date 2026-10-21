package taan.online.match.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record MatchCombatStatistics(
    Map<UUID, Long> damage,
    Map<UUID, Integer> kills,
    Map<UUID, Long> acceptedShots
) {
    public MatchCombatStatistics(Map<UUID, Long> damage) {
        this(damage, Map.of(), Map.of());
    }

    public MatchCombatStatistics {
        damage = Map.copyOf(damage);
        kills = Map.copyOf(kills);
        acceptedShots = Map.copyOf(acceptedShots);
        damage.values().forEach(value -> require(value, 0xffffffffL));
        kills.values().forEach(value -> require(value, 255));
        acceptedShots.values().forEach(value -> require(value, Long.MAX_VALUE));
    }

    public static MatchCombatStatistics empty() {
        return new MatchCombatStatistics(Map.of());
    }

    public long damage(UUID session) {
        return damage.getOrDefault(session, 0L);
    }

    public int kills(UUID session) {
        return kills.getOrDefault(session, 0);
    }

    public long acceptedShots(UUID session) {
        return acceptedShots.getOrDefault(session, 0L);
    }

    public MatchCombatStatistics credit(UUID session, long amount) {
        require(amount, 0xffffffffL);

        if (amount == 0) {
            return this;
        }

        long total = Math.addExact(damage(session), amount);
        require(total, 0xffffffffL);
        var updated = new HashMap<>(damage);
        updated.put(session, total);
        return new MatchCombatStatistics(updated, kills, acceptedShots);
    }

    public MatchCombatStatistics killed(UUID session) {
        var updated = new HashMap<>(kills);
        updated.put(session, Math.incrementExact(kills(session)));
        return new MatchCombatStatistics(damage, updated, acceptedShots);
    }

    public MatchCombatStatistics fired(UUID session) {
        var updated = new HashMap<>(acceptedShots);
        updated.put(session, Math.incrementExact(acceptedShots(session)));
        return new MatchCombatStatistics(damage, kills, updated);
    }

    private static void require(long value, long maximum) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException("Combat statistic is outside its range");
        }
    }
}
