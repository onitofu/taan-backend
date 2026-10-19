package taan.online.match.model;

public final class MatchEnergyRules {
    private MatchEnergyRules() {
    }

    public static int adjust(int current, int delta, int maximum) {
        int value = (short) (current + delta);

        if (value > (short) maximum) {
            value = (short) maximum;
        }

        return Math.max(0, value);
    }

    public static int maximum(int previous, int contribution) {
        return Math.max(previous, (short) contribution);
    }
}
