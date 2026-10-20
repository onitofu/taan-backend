package taan.online.match.service;

import java.io.IOException;
import java.util.Set;
import taan.online.MatchRepeatTestPackets;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchBonusOffer;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.ScenarioApplicationResult;
import taan.online.match.service.model.UseMatchItemCommand;

final class MatchRepeatFixture implements AutoCloseable {
    final EnergyRuntimeFixture energy;
    final MatchRuntimeFixture f;

    MatchRepeatFixture() throws Exception {
        this(new MatchLootPolicy(Set.of(1, 2, 5)));
    }

    MatchRepeatFixture(MatchLootPolicy policy) throws Exception {
        energy = new EnergyRuntimeFixture(policy);
        f = energy.f;
    }

    void grant(int item, boolean confirmed) {
        var member = f.match.participants().getFirst();
        int index = f.match.loot().inventory(member.sessionId()).firstEmpty();
        var loot = f.match.loot().offered(new MatchBonusOffer(
            member.sessionId(),
            member.admissionId(),
            f.match.turn().revision(),
            0,
            false
        )).granted(member.sessionId(), item);

        if (confirmed) {
            loot = loot.confirmed(member.sessionId(), member.admissionId(), index, item);
        }

        f.match.updateLoot(loot);
        f.runtime.refresh();
    }

    UseMatchItemCommand use(int index, int item) {
        var base = energy.use();
        return new UseMatchItemCommand(
            base.context(),
            base.turnRevision(),
            base.phaseRevision(),
            base.slot(),
            base.team(),
            0,
            0,
            index,
            item
        );
    }

    PcGamePlayScenario shot(int count) throws IOException {
        var snapshot = f.runtime.snapshot();
        var actor = snapshot.actors().get(f.room.owner);
        var selected = snapshot.attacks().get(f.room.owner);
        return MatchRepeatTestPackets.shot(0, actor.facing(), selected.selectedItems(), selected.attackKind(), count);
    }

    ScenarioApplicationResult apply(PcGamePlayScenario packet) {
        var base = energy.use();
        var command = new ApplyScenarioCommand(
            base.context(),
            base.turnRevision(),
            f.runtime.snapshot().world().revision(),
            new ScenarioClaimsMapper().map(packet),
            new ScenarioCodec().encode(packet),
            base.phaseRevision()
        );
        var pending = f.runtime.applyScenario(command);
        f.run();
        return pending.join();
    }

    @Override
    public void close() {
        energy.close();
    }
}
