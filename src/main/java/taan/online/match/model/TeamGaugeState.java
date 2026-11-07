package taan.online.match.model;

public record TeamGaugeState(int committed, int pending) {
    public TeamGaugeState {
        committed &= 0xffff;
        pending &= 0xffff;
    }

    public static TeamGaugeState empty() {
        return new TeamGaugeState(0, 0);
    }
}
