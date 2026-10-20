package taan.online.match.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchBoxPolicy;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioBaseCommand;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioIntPoint;
import taan.online.match.protocol.packet.ScenarioMakeItemBoxCommand;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.PassMatchTurnCommand;
import taan.online.match.service.model.ScenarioApplicationResult;

final class MatchBoxFixture implements AutoCloseable {
    final LiveMatchFixture live = new LiveMatchFixture(MatchLootPolicy.disabled(), MatchBoxPolicy.ENABLED);
    final MatchRuntimeFixture f = live.f;

    MatchBoxFixture() throws IOException {
    }

    ScenarioMakeItemBoxCommand box(long id, int tick, int x, int y) {
        int slot = f.runtime.snapshot().turn().activeSlot();
        return new ScenarioMakeItemBoxCommand(
            new ScenarioCommandHeader(slot, 10, tick, 0),
            id,
            new ScenarioIntPoint(x, y)
        );
    }

    ApplyScenarioCommand command(ScenarioMakeItemBoxCommand... additions) throws IOException {
        var base = live.shot(false, false);
        var packet = new ScenarioCodec().decode(base.root());
        var scene = packet.scenario();
        var commands = new ArrayList<ScenarioCommand>(scene.commands().stream()
            .filter(command -> command.header().kind() != 8).toList());
        commands.addAll(List.of(additions));
        int end = commands.stream().mapToInt(command -> {
            var header = command.header();
            return header.tick() + (header.kind() >= 1 && header.kind() <= 3 ? header.scalar10() : 0);
        }).max().orElseThrow() + 1000;
        commands.add(new ScenarioBaseCommand(new ScenarioCommandHeader(packet.shooterSlot(), 8, end, 0)));
        commands.sort(Comparator.comparingInt(command -> command.header().tick()));
        var changed = new PcGamePlayScenario(
            packet.facing(),
            packet.shooterSlot(),
            new GameScenario(
                scene.slot(),
                scene.scalar09(),
                commands,
                scene.selectedItems(),
                scene.effects(),
                scene.flags(),
                scene.power(),
                scene.refittedSlots(),
                scene.scalar0c()
            ),
            packet.scalar18(),
            packet.scalar1c(),
            packet.tail()
        );
        return new ApplyScenarioCommand(
            base.context(),
            base.turnRevision(),
            base.worldRevision(),
            new ScenarioClaimsMapper().map(changed),
            new ScenarioCodec().encode(changed)
        );
    }

    ScenarioApplicationResult apply(ApplyScenarioCommand command) {
        var result = f.runtime.applyScenario(command);
        f.run();
        return result.join();
    }

    void acknowledge() {
        f.runtime.confirmScenario(live.ack(0, false));
        f.runtime.confirmScenario(live.ack(1, false));
        f.run();
    }

    boolean pass() {
        var snapshot = f.runtime.snapshot();
        var member = snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
            .findFirst().orElseThrow();
        var actor = snapshot.actors().get(member.sessionId());
        var command = new PassMatchTurnCommand(
            MatchCommandContext.from(snapshot, member.sessionId()),
            snapshot.turn().revision(),
            snapshot.phaseRevision(),
            member.slotIndex(),
            actor.x(),
            actor.y(),
            actor.facing(),
            0
        );
        var result = f.runtime.turnWork(() -> f.runtime.movement.pass(command));
        f.run();
        return result.join();
    }

    @Override
    public void close() {
        live.close();
    }
}
