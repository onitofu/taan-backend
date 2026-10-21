package taan.online.match.model;

public record MatchShotStatistics(long acceptedShots, long damagingShots) {
    public MatchShotStatistics {
        if (acceptedShots < 0 || damagingShots < 0 || damagingShots > acceptedShots) {
            throw new IllegalArgumentException("Inconsistent shot statistics");
        }
    }

    public float accuracy() {
        return acceptedShots == 0 ? 0 : (float) ((double) damagingShots / acceptedShots);
    }
}
