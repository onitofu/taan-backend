package taan.online.match.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomTeam;

public final class ScenarioGaugeDraft {
    private final Map<RoomTeam, TeamGaugeState> original;
    private final RoomTeam team;
    private final TeamGaugeState initial;
    private final boolean enabled;
    private final int count;
    private final List<Long> pickups = new ArrayList<>();
    private final List<Long> ticks = new ArrayList<>();
    private final List<TeamGaugeDelta> claims = new ArrayList<>();
    private TeamGaugeState replayed;

    public ScenarioGaugeDraft(Map<RoomTeam, TeamGaugeState> original, RoomTeam team, RoomMode mode, int count) {
        this.original = original;
        this.team = team;
        this.count = count;
        enabled = !mode.usesItems();
        initial = original.get(team);
        replayed = initial;
    }

    public void pickup(long tick) {
        pickups.add(tick);
    }

    public void claim(long tick, TeamGaugeDelta delta) {
        require(enabled && TeamGaugeRules.level(count, initial.committed()) < 4, "GAUGE_DISABLED");
        require(delta.reason() == 0 || delta.reason() == 1, "INVALID_GAUGE_REASON");
        ticks.add(tick);
        claims.add(delta);
        replayed = TeamGaugeRules.replay(delta);
    }

    public Map<RoomTeam, TeamGaugeState> finish(boolean damagingHit) {
        if (!enabled || TeamGaugeRules.level(count, initial.committed()) >= 4) {
            return original;
        }

        var remaining = new ArrayList<Integer>();

        for (int index = 0; index < claims.size(); index++) {
            if (claims.get(index).reason() == 0) {
                remaining.add(index);
            }
        }

        require(remaining.size() == pickups.size(), "MISSING_PICKUP_GAUGE");
        var pickupTicks = new ArrayList<>(pickups);
        int pending = initial.pending();

        while (!remaining.isEmpty()) {
            int expected = pending;
            int index = remaining.stream().filter(at -> claims.get(at).oldGauge() == expected).findFirst()
                .orElseThrow(() -> new ScenarioWorldException("INVALID_GAUGE_CHAIN"));
            require(pickupTicks.remove(ticks.get(index)), "INVALID_GAUGE_PICKUP_TICK");
            pending = checked(claims.get(index), pending, 50, 50);
            remaining.remove(Integer.valueOf(index));
        }

        var damage = claims.stream().filter(delta -> delta.reason() == 1).toList();
        require(damage.size() <= 1, "DUPLICATE_DAMAGE_GAUGE");

        if (!damage.isEmpty()) {
            require(damagingHit, "GAUGE_WITHOUT_DAMAGE");
            checked(damage.getFirst(), pending, 1, 20);
        }

        var result = new HashMap<>(original);
        result.put(team, replayed);
        return Map.copyOf(result);
    }

    private int checked(TeamGaugeDelta delta, int pending, int min, int max) {
        int gain = (delta.newGauge() - pending) & 0xffff;
        require(delta.oldGauge() == pending && gain >= min && gain <= max, "INVALID_GAUGE_DELTA");
        return delta.newGauge();
    }

    private void require(boolean condition, String reason) {
        if (!condition) {
            throw new ScenarioWorldException(reason);
        }
    }
}
