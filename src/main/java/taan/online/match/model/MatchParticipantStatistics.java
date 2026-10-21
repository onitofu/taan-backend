package taan.online.match.model;

public record MatchParticipantStatistics(
    int maxHp,
    long acceptedShots,
    long longestTurnMillis,
    boolean voluntarilyPassed,
    Integer rewardDraw
) {
    public MatchParticipantStatistics(
        int maxHp,
        long acceptedShots,
        long longestTurnMillis,
        boolean voluntarilyPassed
    ) {
        this(maxHp, acceptedShots, longestTurnMillis, voluntarilyPassed, null);
    }

    public MatchParticipantStatistics withDraw(int draw) {
        return new MatchParticipantStatistics(maxHp, acceptedShots, longestTurnMillis, voluntarilyPassed, draw);
    }

    public MatchParticipantStatistics {
        if (maxHp < 1 || acceptedShots < 0 || longestTurnMillis < 0
            || rewardDraw != null && (rewardDraw < 0 || rewardDraw > 99)) {
            throw new IllegalArgumentException("Invalid reward participation statistics");
        }
    }

    public boolean hadOpportunity() {
        return longestTurnMillis >= 5000 || voluntarilyPassed;
    }
}
