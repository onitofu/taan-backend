package taan.online.match.model;

public record TeamGaugeDelta(int reason, int newGauge, int oldGauge) {
    public TeamGaugeDelta {
        if (reason < 0 || reason > 255) {
            throw new IllegalArgumentException("Invalid team gauge reason");
        }

        newGauge &= 0xffff;
        oldGauge &= 0xffff;
    }
}
