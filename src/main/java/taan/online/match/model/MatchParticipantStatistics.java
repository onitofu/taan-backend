package taan.online.match.model;

public record MatchParticipantStatistics(
    int maxHp,
    long acceptedShots,
    long longestTurnMillis,
    boolean voluntarilyPassed
) {
    public MatchParticipantStatistics {
        if (maxHp < 1 || acceptedShots < 0 || longestTurnMillis < 0) {
            throw new IllegalArgumentException("Invalid reward participation statistics");
        }
    }

    public boolean hadOpportunity() {
        return longestTurnMillis >= 5000 || voluntarilyPassed;
    }
}
