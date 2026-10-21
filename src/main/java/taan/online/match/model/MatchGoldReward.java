package taan.online.match.model;

public record MatchGoldReward(long before, long after, long baseDelta, long medalDelta, long cardDelta) {
    public MatchGoldReward {
        if (before < 0 || before > 0xffff_ffffL || after < before || after > 0xffff_ffffL
            || baseDelta < 0 || medalDelta < 0 || cardDelta < 0
            || Math.addExact(Math.addExact(baseDelta, medalDelta), cardDelta) != after - before) {
            throw new IllegalArgumentException("Inconsistent gold reward");
        }
    }

    public long delta() {
        return after - before;
    }
}
