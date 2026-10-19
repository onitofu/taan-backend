package taan.online.match.model;

public record MatchActorState(int x, int y, int facing, int hp, int energy) {
    public MatchActorState {
        if (facing < 0 || facing > 1 || hp < 0 || energy < 0) {
            throw new IllegalArgumentException("Invalid match actor state");
        }
    }

    public boolean dead() {
        return hp == 0;
    }
}
