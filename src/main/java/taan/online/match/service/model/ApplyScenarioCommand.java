package taan.online.match.service.model;

public record ApplyScenarioCommand(
    MatchCommandContext context,
    long turnRevision,
    long worldRevision,
    ScenarioClaims claims,
    byte[] root
) {
    public ApplyScenarioCommand {
        root = root.clone();
    }

    @Override
    public byte[] root() {
        return root.clone();
    }

    @Override
    public String toString() {
        return "ApplyScenarioCommand[REDACTED]";
    }
}
