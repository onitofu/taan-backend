package taan.online.match.service;

import java.util.List;
import taan.online.MatchScenarioTestPackets;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.protocol.packet.ScenarioWeaponFields;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.match.service.model.SpentMatchItem;
import taan.online.match.service.model.UseMatchItemCommand;

final class EnergyRuntimeFixture implements AutoCloseable {
    final MovementRuntimeFixture movement;
    final MatchRuntimeFixture f;

    EnergyRuntimeFixture() throws Exception {
        this(MatchLootPolicy.disabled());
    }

    EnergyRuntimeFixture(MatchLootPolicy policy) throws Exception {
        movement = new MovementRuntimeFixture(policy);
        f = movement.f;
    }

    MatchSnapshot snapshot() {
        return f.runtime.snapshot();
    }

    MatchCommandContext context() {
        var snapshot = snapshot();
        var member = snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
            .findFirst().orElseThrow();
        return MatchCommandContext.from(snapshot, member.sessionId());
    }

    UseMatchItemCommand use() {
        var member = snapshot().participants().stream().filter(p -> p.sessionId().equals(context().sessionId()))
            .findFirst().orElseThrow();
        return new UseMatchItemCommand(
            context(),
            snapshot().turn().revision(),
            snapshot().phaseRevision(),
            member.slotIndex(),
            member.slotIndex() == 0 ? 1 : 2,
            0,
            0,
            255,
            6
        );
    }

    RecordMatchSpendCommand spend(List<SpentMatchItem> items) {
        var use = use();
        return new RecordMatchSpendCommand(
            use.context(),
            use.turnRevision(),
            use.phaseRevision(),
            use.slot(),
            use.team(),
            255,
            items
        );
    }

    boolean activate(UseMatchItemCommand command) {
        var result = f.runtime.turnWork(() -> f.runtime.items.activate(command));
        f.run();
        return result.join();
    }

    boolean record(RecordMatchSpendCommand command) {
        var result = f.runtime.turnWork(() -> f.runtime.items.record(command));
        f.run();
        return result.join();
    }

    void charge() {
        for (int turn = 0; turn < 20; turn++) {
            movement.pass(movement.pass());
        }
    }

    ApplyScenarioCommand shot(boolean special, int grant) throws Exception {
        var use = use();
        var packet = MatchScenarioTestPackets.shot(use.slot(), use.slot() == 0 ? 0 : 1, 0, false);
        var scene = packet.scenario();
        var commands = scene.commands().stream().map(event -> special ? special(event) : event).toList();
        var selected = special ? List.of(6) : List.<Integer>of();
        var modified = new PcGamePlayScenario(
            packet.facing(),
            packet.shooterSlot(),
            new GameScenario(
                scene.slot(),
                scene.scalar09(),
                commands,
                selected,
                List.of(),
                special ? 1 : 0,
                scene.power(),
                scene.refittedSlots(),
                scene.scalar0c()
            ),
            packet.scalar18(),
            grant,
            packet.tail()
        );
        return new ApplyScenarioCommand(
            context(),
            snapshot().turn().revision(),
            snapshot().world().revision(),
            new ScenarioClaimsMapper().map(modified),
            new ScenarioCodec().encode(modified),
            snapshot().phaseRevision()
        );
    }

    private ScenarioCommand special(ScenarioCommand command) {
        if (command instanceof ScenarioFireCommand fire) {
            return new ScenarioFireCommand(
                fire.header(),
                weapon(fire.weapon()),
                0,
                2,
                fire.velocity(),
                fire.acceleration(),
                fire.byte40(),
                fire.word42()
            );
        }

        if (command instanceof ScenarioWeaponCommand weapon) {
            return new ScenarioWeaponCommand(weapon.header(), weapon(weapon.weapon()));
        }

        if (command instanceof ScenarioNormalCommand normal && normal.header().kind() == 9) {
            return new ScenarioNormalCommand(normal.header(), 11, normal.scalar18());
        }

        return command;
    }

    private ScenarioWeaponFields weapon(ScenarioWeaponFields weapon) {
        return new ScenarioWeaponFields(
            weapon.sequence(),
            2,
            weapon.position(),
            weapon.shotTick(),
            weapon.scalar24Bits(),
            weapon.scalar28()
        );
    }

    void acknowledge() {
        f.runtime.confirmScenario(movement.live.ack(0, false));
        f.runtime.confirmScenario(movement.live.ack(1, false));
        f.run();
    }

    @Override
    public void close() {
        movement.close();
    }
}
