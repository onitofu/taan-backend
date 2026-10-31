package taan.online.match.model;

public final class MatchMedalLimitException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public MatchMedalLimitException() {
        super("Character medal counter cannot be incremented");
    }
}
