package taan.online.logging;

final class WarningWindow {
    private long started;
    private int emitted;
    private long suppressed;

    WarningWindow(long started) {
        this.started = started;
    }

    long started() {
        return started;
    }

    int emitted() {
        return emitted;
    }

    long suppressed() {
        return suppressed;
    }

    void reset(long now) {
        started = now;
        emitted = 0;
        suppressed = 0;
    }

    void recordEmission() {
        emitted++;
    }

    void recordSuppression() {
        suppressed++;
    }
}
