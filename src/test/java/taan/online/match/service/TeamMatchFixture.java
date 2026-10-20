package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import taan.online.MatchFixtures;
import taan.online.MatchGuidedTestPackets;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.TeamScenarioPackets;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchAttackSelection;
import taan.online.match.model.MatchItemSelectionRules;
import taan.online.match.model.MatchParticipant;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.PassMatchTurnCommand;
import taan.online.match.service.model.ScenarioApplicationResult;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.RoomMatchToken;

final class TeamMatchFixture implements AutoCloseable {
    final RoomScenario scenario = new RoomScenario(MatchExecutionMode.CLIENT_SCENARIO, MatchGuidedTestPackets.POLICY);
    final List<UUID> players = new ArrayList<>();
    final Map<UUID, TestMatchOutput> outputs = new LinkedHashMap<>();
    final UUID owner;
    final RoomMode mode;
    RoomMatchToken token;

    TeamMatchFixture(int blue, int red, RoomMode mode) {
        this.mode = mode;

        for (int index = 0; index < blue + red; index++) {
            var id = scenario.player(index + 1, 1);
            players.add(id);

            if (index == 0) {
                scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput());
            } else {
                scenario.enter.execute(id, 1, "", new RecordingRoomOutput());
            }

            scenario.rooms.changeTeam(id, index < blue ? RoomTeam.BLUE : RoomTeam.RED);
            assertEquals(index < blue ? RoomTeam.BLUE : RoomTeam.RED, scenario.rooms.current(id).orElseThrow()
                .slots().stream().filter(s -> s.participant() != null && s.participant().sessionId().equals(id))
                .findFirst().orElseThrow().team());
            var change = scenario.rooms.changeWeapon(id, index + 1, new int[]{0, 8, 10}[index % 3]);
            assertTrue(change.reason().equals("CHANGED") || change.reason().equals("UNCHANGED"), change.reason());
            var output = new TestMatchOutput();
            outputs.put(id, output);
            scenario.matches.subscribe(id, output);
        }

        owner = players.getFirst();
        scenario.rooms.changeSettings(owner, 1, mode == RoomMode.ITEM ? 0 : 2, 2);
    }

    void start(boolean loaded) {
        players.stream().skip(1).forEach(id -> scenario.rooms.setReady(id, true));

        if (mode.requiresCaptainSelection()) {
            scenario.rooms.beginCaptainSelection(owner);
            scenario.timers.advanceSeconds(5);
        }

        var result = scenario.start.execute(owner);
        assertEquals("STARTED", result.reason());
        result.delivery().toCompletableFuture().join();
        token = scenario.rooms.matchContext(owner).token();

        if (loaded) {
            MatchFixtures.loaded(scenario.matches, token);
        }
    }

    MatchSnapshot snapshot() {
        return scenario.matches.current(token).orElseThrow();
    }

    MatchParticipant active() {
        var snapshot = snapshot();
        return snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
            .findFirst().orElseThrow();
    }

    MatchCommandContext context(UUID id) {
        return MatchCommandContext.from(snapshot(), id);
    }

    List<Integer> selected() {
        var snapshot = snapshot();
        var member = active();
        return MatchItemSelectionRules.selected(
            snapshot.attacks().getOrDefault(member.sessionId(), MatchAttackSelection.NORMAL)
                .activeItems(),
            snapshot.captain(member.slotIndex())
        );
    }

    ScenarioApplicationResult shot(int target, int damage) throws Exception {
        return apply(TeamScenarioPackets.shot(snapshot(), selected(), target, damage));
    }

    ScenarioApplicationResult apply(PcGamePlayScenario packet) {
        var snapshot = snapshot();
        return scenario.matches.applyScenario(new ApplyScenarioCommand(
            context(active().sessionId()),
            snapshot.turn().revision(),
            snapshot.world().revision(),
            new ScenarioClaimsMapper().map(packet),
            new ScenarioCodec().encode(packet),
            snapshot.phaseRevision()
        )).toCompletableFuture().join();
    }

    boolean ack(UUID id) {
        var snapshot = snapshot();
        return scenario.matches.confirmScenario(new ConfirmScenarioCommand(
            context(id),
            snapshot.turn().revision(),
            snapshot.world().revision(),
            Optional.of(snapshot.completion().orElseThrow().fingerprint()),
            snapshot.turn().activeSlot(),
            snapshot.phaseRevision()
        )).toCompletableFuture().join();
    }

    void acknowledge() {
        var pending = snapshot().completion().orElseThrow();

        for (var id : pending.awaitingSessions()) {
            assertTrue(ack(id));
        }
    }

    void weapon(int weapon) {
        for (int step = 0; step < players.size() && active().weaponId() != weapon; step++) {
            pass();
        }

        assertEquals(weapon, active().weaponId());
    }

    void pass() {
        var snapshot = snapshot();
        var member = active();
        var actor = snapshot.actors().get(member.sessionId());
        assertTrue(scenario.matches.passTurn(new PassMatchTurnCommand(
            context(member.sessionId()),
            snapshot.turn().revision(),
            snapshot.phaseRevision(),
            member.slotIndex(),
            actor.x(),
            actor.y(),
            actor.facing(),
            0
        )).toCompletableFuture().join());
    }

    @Override
    public void close() {
        scenario.close();
    }
}
