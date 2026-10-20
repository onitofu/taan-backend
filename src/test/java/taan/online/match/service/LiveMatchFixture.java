package taan.online.match.service;

import java.io.IOException;
import java.util.Optional;
import taan.online.MatchScenarioTestPackets;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchBoxPolicy;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchLootPolicy;

final class LiveMatchFixture implements AutoCloseable {
    final ScenarioRuntimeFixture scenario;
    final MatchRuntimeFixture f;

    LiveMatchFixture() throws IOException {
        this(MatchLootPolicy.disabled());
    }

    LiveMatchFixture(MatchLootPolicy policy) throws IOException {
        this(policy, MatchBoxPolicy.DISABLED);
    }

    LiveMatchFixture(MatchLootPolicy policy, MatchBoxPolicy boxes) throws IOException {
        scenario = new ScenarioRuntimeFixture(8, MatchExecutionMode.CANNON_1V1, policy, boxes);
        f = scenario.f;
    }

    ApplyScenarioCommand shot(boolean fall, boolean lethal) throws IOException {
        var snapshot = f.runtime.snapshot();
        int slot = snapshot.turn().activeSlot();
        var shooter = snapshot.participants().stream().filter(p -> p.slotIndex() == slot).findFirst().orElseThrow();
        var context = new MatchCommandContext(
            snapshot.id(),
            snapshot.roomId(),
            snapshot.roomGeneration(),
            snapshot.channelId(),
            shooter.sessionId(),
            shooter.admissionId()
        );
        var request = MatchScenarioTestPackets.shot(slot, shooter.facing(), lethal ? 10000 : 77, fall);
        return new ApplyScenarioCommand(
            context,
            snapshot.turn().revision(),
            snapshot.world().revision(),
            new ScenarioClaimsMapper().map(request),
            new ScenarioCodec().encode(request)
        );
    }

    ConfirmScenarioCommand ack(int member, boolean coalesced) {
        var snapshot = f.runtime.snapshot();
        var p = snapshot.participants().get(member);
        var pending = snapshot.completion().orElseThrow();
        return new ConfirmScenarioCommand(
            new MatchCommandContext(
                snapshot.id(),
                snapshot.roomId(),
                snapshot.roomGeneration(),
                snapshot.channelId(),
                p.sessionId(),
                p.admissionId()
            ),
            snapshot.turn().revision(),
            snapshot.world().revision() - (coalesced ? 1 : 0),
            coalesced ? Optional.empty() : Optional.of(pending.fingerprint()),
            snapshot.turn().activeSlot()
        );
    }

    @Override
    public void close() {
        scenario.close();
    }
}
