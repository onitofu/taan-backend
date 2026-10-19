package taan.online.match.controller;

import java.util.concurrent.CompletionStage;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.service.ApplyScenarioAction;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.ScenarioApplicationResult;

public final class ApplyScenarioPacketHandler {
    private final ApplyScenarioAction action;
    private final ScenarioCodec codec = new ScenarioCodec();
    private final ScenarioClaimsMapper mapper = new ScenarioClaimsMapper();

    public ApplyScenarioPacketHandler(ApplyScenarioAction action) {
        this.action = action;
    }

    public CompletionStage<ScenarioApplicationResult> handle(
        MatchCommandContext context,
        long turnRevision,
        long worldRevision,
        byte[] root
    ) {
        byte[] original = root.clone();
        var packet = codec.decode(original);
        return action.execute(new ApplyScenarioCommand(
            context,
            turnRevision,
            worldRevision,
            mapper.map(packet),
            original
        ));
    }
}
