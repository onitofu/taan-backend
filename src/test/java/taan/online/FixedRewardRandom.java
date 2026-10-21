package taan.online;

import java.util.random.RandomGenerator;

public final class FixedRewardRandom implements RandomGenerator {
    private final int draw;
    public int calls;

    public FixedRewardRandom(int draw) {
        this.draw = draw;
    }

    @Override
    public int nextInt(int bound) {
        calls++;
        return Math.floorMod(draw, bound);
    }

    @Override
    public long nextLong() {
        return draw;
    }
}
