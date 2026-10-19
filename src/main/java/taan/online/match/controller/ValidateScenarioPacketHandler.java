package taan.online.match.controller;

import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.service.ValidateScenarioAction;
import taan.online.match.service.model.ScenarioAdmissionContext;
import taan.online.match.service.model.ScenarioAdmissionResult;

public final class ValidateScenarioPacketHandler {
    private final ValidateScenarioAction action;
    private final ScenarioCodec codec = new ScenarioCodec();
    private final ScenarioClaimsMapper mapper = new ScenarioClaimsMapper();

    public ValidateScenarioPacketHandler(ValidateScenarioAction action) {
        this.action = action;
    }

    public ScenarioAdmissionResult handle(ScenarioAdmissionContext context, byte[] root) {
        byte[] original = root.clone();
        return action.execute(context, mapper.map(codec.decode(original)), original);
    }
}
