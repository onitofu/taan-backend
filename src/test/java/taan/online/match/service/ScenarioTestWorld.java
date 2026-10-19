package taan.online.match.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import taan.online.MatchFixtures;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchActorState;
import taan.online.match.model.MatchDefinitions;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.MatchTurn;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.service.model.AcceptedScenario;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.ScenarioAdmissionContext;
import taan.online.match.service.model.ScenarioAdmissionResult;
import taan.online.match.service.model.ScenarioClaim;
import taan.online.match.service.model.ScenarioClaims;
import taan.online.room.model.RoomId;

public final class ScenarioTestWorld implements AutoCloseable {
    private final MatchRuntimeFixture fixture = new MatchRuntimeFixture(8);
    public final MatchSnapshot original = fixture.runtime.snapshot();
    public MatchId id = original.id();
    public RoomId roomId = original.roomId();
    public UUID generation = original.roomGeneration();
    public int channel = original.channelId();
    public List<MatchParticipant> members = original.participants();
    public final Map<UUID, MatchActorState> actors = new HashMap<>();
    public MatchDefinitions definitions = original.definitions();
    public MatchPhase phase = MatchPhase.ACTION;
    public int activeSlot = 2;
    public int boxBudget;
    public long revision = 7;
    public boolean current = true;
    public final byte[] root;
    public final ScenarioClaims claims;
    public final MatchCommandContext sender;
    public final ValidateScenarioAction action = new ValidateScenarioAction((context, participant) -> current);

    public ScenarioTestWorld() throws IOException {
        root = MatchFixtures.root("native-cannon-2-0");
        claims = new ScenarioClaimsMapper().map(new ScenarioCodec().decode(root));

        for (var member : members) {
            actors.put(member.sessionId(), new MatchActorState(500, 650, 0, 1000, 20));
        }

        var shooter = members.stream().filter(member -> member.slotIndex() == 2).findFirst().orElseThrow();
        sender = context(shooter.sessionId(), shooter.admissionId());
    }

    public MatchCommandContext context(UUID session, UUID admission) {
        return new MatchCommandContext(id, roomId, generation, channel, session, admission);
    }

    public MatchSnapshot snapshot() {
        return new MatchSnapshot(
            id,
            roomId,
            generation,
            channel,
            original.ownerSessionId(),
            original.mode(),
            original.rule(),
            definitions,
            members,
            original.captains(),
            phase,
            original.loadedSessions(),
            actors,
            new MatchTurn(activeSlot, 0, 0, boxBudget, 0, List.of(0, 2), revision, OptionalLong.of(1)),
            3,
            100
        );
    }

    public ScenarioAdmissionContext admission() {
        return admission(sender, Optional.empty());
    }

    public ScenarioAdmissionContext admission(MatchCommandContext context, Optional<AcceptedScenario> previous) {
        return new ScenarioAdmissionContext(snapshot(), context, 7, previous);
    }

    public ScenarioAdmissionResult execute(ScenarioClaims candidate) {
        return action.execute(admission(), candidate, root);
    }

    public ScenarioClaims commands(List<ScenarioClaim> commands) {
        return new ScenarioClaims(
            claims.shooterSlot(),
            claims.scenarioSlot(),
            claims.facing(),
            claims.flags(),
            claims.power(),
            claims.selectedItems(),
            claims.effectsPresent(),
            claims.refittedSlots(),
            commands
        );
    }

    @Override
    public void close() {
        fixture.close();
    }
}
