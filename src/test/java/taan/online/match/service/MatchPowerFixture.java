package taan.online.match.service;

import java.util.Set;
import taan.online.MatchPowerTestPackets;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchBonusOffer;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.ScenarioApplicationResult;
import taan.online.match.service.model.UseMatchItemCommand;

final class MatchPowerFixture implements AutoCloseable {
    final EnergyRuntimeFixture energy;
    final MatchRuntimeFixture f;

    MatchPowerFixture() throws Exception {
        this(new MatchLootPolicy(Set.of(5)));
    }

    MatchPowerFixture(MatchLootPolicy policy) throws Exception {
        energy = new EnergyRuntimeFixture(policy);
        f = energy.f;
    }

    void grant(int slot, int item, boolean confirmed) {
        var snapshot = f.runtime.snapshot();
        var actor = snapshot.participants().stream().filter(member -> member.slotIndex() == slot)
            .findFirst().orElseThrow();
        var loot = f.match.loot().offered(new MatchBonusOffer(
            actor.sessionId(),
            actor.admissionId(),
            snapshot.turn().revision(),
            0,
            false
        )).granted(actor.sessionId(), item);

        if (confirmed) {
            int index = loot.inventory(actor.sessionId()).firstEmpty();
            loot = loot.confirmed(actor.sessionId(), actor.admissionId(), index < 0 ? 2 : index - 1, item);
        }

        f.match.updateLoot(loot);
        f.runtime.refresh();
    }

    UseMatchItemCommand use(int index, int item) {
        var special = energy.use();
        return new UseMatchItemCommand(
            special.context(),
            special.turnRevision(),
            special.phaseRevision(),
            special.slot(),
            special.team(),
            0,
            0,
            index,
            item
        );
    }

    ApplyScenarioCommand shot() throws Exception {
        return shot(true);
    }

    ApplyScenarioCommand shot(boolean powered) throws Exception {
        var base = energy.shot(false, 0);
        var original = new ScenarioCodec().decode(base.root());
        var packet = powered ? MatchPowerTestPackets.power(original) : MatchPowerTestPackets.unpowered(original);
        return new ApplyScenarioCommand(
            base.context(),
            base.turnRevision(),
            base.worldRevision(),
            new ScenarioClaimsMapper().map(packet),
            new ScenarioCodec().encode(packet),
            f.runtime.snapshot().phaseRevision()
        );
    }

    ScenarioApplicationResult apply(ApplyScenarioCommand command) {
        var result = f.runtime.applyScenario(command);
        f.run();
        return result.join();
    }

    MatchCommandContext context(int slot) {
        var snapshot = f.runtime.snapshot();
        var member = snapshot.participants().stream().filter(p -> p.slotIndex() == slot).findFirst().orElseThrow();
        return MatchCommandContext.from(snapshot, member.sessionId());
    }

    @Override
    public void close() {
        energy.close();
    }
}
