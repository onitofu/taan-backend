package taan.online.match.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import taan.online.MatchFixtures;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ConfirmMatchLoadingCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.StartMatchCommand;

final class ScenarioRuntimeFixture implements AutoCloseable {
    final MatchRuntimeFixture f;
    final PcGamePlayScenario nativePacket;

    ScenarioRuntimeFixture() throws IOException {
        this(8, MatchExecutionMode.SCENARIO_PREPARATION);
    }

    ScenarioRuntimeFixture(int capacity, MatchExecutionMode mode) throws IOException {
        f = new MatchRuntimeFixture(capacity, mode);
        nativePacket = new ScenarioCodec().decode(MatchFixtures.root("native-cannon-0-0"));
        f.activate();
        f.runtime.submit(new StartMatchCommand(f.token));
        f.run();

        for (var member : f.runtime.snapshot().participants()) {
            var current = f.runtime.snapshot();
            var context = new MatchCommandContext(
                current.id(),
                current.roomId(),
                current.roomGeneration(),
                current.channelId(),
                member.sessionId(),
                member.admissionId()
            );
            f.runtime.submit(
                new ConfirmMatchLoadingCommand(
                    context,
                    member.slotIndex(),
                    member.spawn().settledX(),
                    member.spawn().settledY()
                )
            );
        }

        f.run();
    }

    ApplyScenarioCommand command() {
        return command(commands());
    }

    ArrayList<ScenarioCommand> commands() {
        var events = new ArrayList<>(nativePacket.scenario().commands().stream()
            .filter(event -> event.header().kind() < 10).toList());
        events.add(new ScenarioDamageCommand(new ScenarioCommandHeader(2, 4, 995, 0), 77, 123456, 5, 30));
        return events;
    }

    ApplyScenarioCommand command(ArrayList<ScenarioCommand> commands) {
        commands.sort(Comparator.comparingLong(command -> Integer.toUnsignedLong(command.header().tick())));
        var original = nativePacket.scenario();
        var scene = new GameScenario(
            original.slot(),
            original.scalar09(),
            commands,
            original.selectedItems(),
            original.effects(),
            original.flags(),
            original.power(),
            original.refittedSlots(),
            original.scalar0c()
        );
        var packet = new PcGamePlayScenario(
            nativePacket.facing(),
            nativePacket.shooterSlot(),
            scene,
            nativePacket.scalar18(),
            nativePacket.scalar1c(),
            nativePacket.tail()
        );
        var snapshot = f.runtime.snapshot();
        return new ApplyScenarioCommand(
            f.context(),
            snapshot.turn().revision(),
            snapshot.world().revision(),
            new ScenarioClaimsMapper().map(packet),
            new ScenarioCodec().encode(packet)
        );
    }

    @Override
    public void close() {
        f.close();
    }
}
