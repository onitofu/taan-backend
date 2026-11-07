package taan.online.match.model;

import java.util.List;
import java.util.Optional;

public final class TeamGaugeRules {
    private TeamGaugeRules() {
    }

    public static int level(int rosterCount, int committed) {
        return Math.min(4, (committed & 0xffff) / threshold(rosterCount, 0));
    }

    public static int threshold(int rosterCount, int level) {
        int step = step(rosterCount);
        return level < 0 || level > 4 ? 1000 : Math.min(level + 1, 4) * step;
    }

    public static Optional<TeamGaugeDelta> generate(TeamGaugeState state, int count, int reason, int delta) {
        return level(count, state.committed()) >= 4 ? Optional.empty()
            : Optional.of(new TeamGaugeDelta(reason, state.pending() + delta, state.pending()));
    }

    public static TeamGaugeState replay(TeamGaugeDelta delta) {
        return new TeamGaugeState(delta.newGauge(), delta.newGauge());
    }

    public static List<Integer> items(int level) {
        return switch (level) {
            case 0 -> List.of();
            case 1 -> List.of(1030);
            case 2 -> List.of(1);
            case 3 -> List.of(2);
            case 4 -> List.of(2, 3);
            default -> throw new IllegalArgumentException("Invalid team gauge level");
        };
    }

    private static int step(int count) {
        if (count < 0 || count > 8) {
            throw new IllegalArgumentException("Invalid team gauge roster count");
        }

        if (count >= 8) {
            return 175;
        }

        if (count >= 6) {
            return 150;
        }

        return count >= 4 ? 125 : 100;
    }
}
