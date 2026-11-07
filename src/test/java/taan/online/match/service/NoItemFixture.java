package taan.online.match.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import taan.online.TeamScenarioPackets;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.Match;
import taan.online.match.model.MatchAttackSelection;
import taan.online.match.model.MatchBoxPolicy;
import taan.online.match.model.MatchItemSelectionRules;
import taan.online.match.model.MatchWorldState;
import taan.online.match.model.TeamGaugeRules;
import taan.online.match.model.TeamGaugeState;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioBaseCommand;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.ReportMatchBonusCommand;
import taan.online.match.service.model.ScenarioApplicationResult;
import taan.online.match.service.model.ScenarioEventTime;
import taan.online.match.service.model.ScenarioTeamGaugeClaim;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomTeam;

final class NoItemFixture implements AutoCloseable {
    final ScenarioRuntimeFixture scenario;
    final MatchRuntimeFixture f;

    NoItemFixture(RoomMode mode, RoomRule rule, int count) throws Exception {
        this(mode, rule, count, MatchLootPolicy.nativeItems());
    }

    NoItemFixture(RoomMode mode, RoomRule rule, int count, MatchLootPolicy policy) throws Exception {
        scenario = new ScenarioRuntimeFixture(
            16,
            MatchExecutionMode.CLIENT_SCENARIO,
            policy,
            MatchBoxPolicy.ENABLED,
            rule,
            mode,
            count
        );
        f = scenario.f;
    }

    MatchSnapshot snapshot() {
        return f.runtime.snapshot();
    }

    MatchCommandContext context(int slot) {
        var member = snapshot().participants().stream().filter(p -> p.slotIndex() == slot).findFirst().orElseThrow();
        return MatchCommandContext.from(snapshot(), member.sessionId());
    }

    void world(MatchWorldState value) throws Exception {
        var field = Match.class.getDeclaredField("world");
        field.setAccessible(true);
        field.set(f.match, value);
        f.runtime.refresh();
    }

    void gauge(RoomTeam team, int value) throws Exception {
        var gauges = new HashMap<>(snapshot().world().teamGauges());
        gauges.put(team, new TeamGaugeState(value, value));
        world(snapshot().world().gauges(gauges));
    }

    ApplyScenarioCommand shot(ScenarioCommand... additions) throws Exception {
        var snapshot = snapshot();
        int slot = snapshot.turn().activeSlot();
        var member = snapshot.participants().stream().filter(p -> p.slotIndex() == slot).findFirst().orElseThrow();
        var active = snapshot.attacks().getOrDefault(member.sessionId(), MatchAttackSelection.NORMAL).activeItems();
        var selected = MatchItemSelectionRules.selected(
            active,
            snapshot.captain(slot),
            TeamGaugeRules.items(TeamGaugeRules.level(
                snapshot.participants().size(),
                snapshot.world().teamGauges().get(member.team()).committed()
            ))
        );
        var target = snapshot.participants().stream().filter(p -> p.team() != member.team()).findFirst().orElseThrow();
        var packet = TeamScenarioPackets.shot(snapshot, selected, target.slotIndex(), 5);
        var scene = packet.scenario();
        var commands = new ArrayList<>(scene.commands().stream().filter(c -> c.header().kind() != 8).toList());
        commands.addAll(List.of(additions));
        int end = commands.stream().mapToInt(c -> c.header().tick()).max().orElseThrow() + 1000;
        commands.add(new ScenarioBaseCommand(new ScenarioCommandHeader(slot, 8, end, 0)));
        commands.sort(Comparator.comparingInt(c -> c.header().tick()));
        var changed = new PcGamePlayScenario(
            packet.facing(),
            slot,
            new GameScenario(
                slot,
                scene.scalar09(),
                commands,
                selected,
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
            context(slot),
            snapshot.turn().revision(),
            snapshot.world().revision(),
            new ScenarioClaimsMapper().map(changed),
            new ScenarioCodec().encode(changed),
            snapshot.phaseRevision()
        );
    }

    ScenarioApplicationResult apply(ApplyScenarioCommand command) {
        var result = f.runtime.applyScenario(command);
        f.run();
        return result.join();
    }

    boolean report(ReportMatchBonusCommand command) {
        var result = MatchItemExecution.reportBonus(f.runtime, command);
        f.run();
        return result.toCompletableFuture().join();
    }

    ReportMatchBonusCommand quiz(int slot, int old) {
        return new ReportMatchBonusCommand(
            context(slot),
            slot,
            slot,
            Optional.of(new ScenarioTeamGaugeClaim(new ScenarioEventTime(slot, 12, 0, 0), -3, 4, old + 5, old)),
            -1L,
            0,
            -17,
            -3,
            -1
        );
    }

    void acknowledge() {
        var completion = snapshot().completion().orElseThrow();
        f.timers.now = completion.settleNotBeforeNanos();

        for (var member : snapshot().participants()) {
            f.runtime.confirmScenario(new ConfirmScenarioCommand(
                context(member.slotIndex()),
                snapshot().turn().revision(),
                completion.worldRevision(),
                Optional.of(completion.fingerprint()),
                snapshot().turn().activeSlot()
            ));
        }

        f.run();
    }

    @Override
    public void close() {
        scenario.close();
    }
}
