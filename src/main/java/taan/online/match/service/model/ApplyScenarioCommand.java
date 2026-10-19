package taan.online.match.service.model;

public record ApplyScenarioCommand(
    MatchCommandContext context,
    long turnRevision,
    long worldRevision,
    ScenarioClaims claims,
    byte[] root,
    long phaseRevision
) {
    public ApplyScenarioCommand(
        MatchCommandContext context,
        long turnRevision,
        long worldRevision,
        ScenarioClaims claims,
        byte[] root
    ) {
        this(context, turnRevision, worldRevision, claims, root, -1);
    }

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
