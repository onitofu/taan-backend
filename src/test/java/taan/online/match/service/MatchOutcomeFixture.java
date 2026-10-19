package taan.online.match.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.MatchScenarioTestPackets;
import taan.online.RoomScenario;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.ScenarioApplicationResult;
import taan.online.room.model.RoomMode;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchToken;

final class MatchOutcomeFixture implements AutoCloseable {
    final MatchRoomFixture room;
    final MatchRegistry matches;
    final TestMatchOutput owner = new TestMatchOutput();
    final TestMatchOutput guest = new TestMatchOutput();
    RoomMatchToken token;

    MatchOutcomeFixture() {
        this(new RoomScenario(MatchExecutionMode.CANNON_1V1));
    }

    MatchOutcomeFixture(RoomScenario scenario) {
        room = new MatchRoomFixture(scenario, 2, RoomMode.ITEM);
        matches = room.scenario.matches;
        matches.subscribe(room.owner, owner);
        matches.subscribe(room.players.get(1), guest);
        start();
    }

    void start() {
        room.scenario.start.execute(room.owner).delivery().toCompletableFuture().join();
        token = room.scenario.rooms.matchContext(room.owner).token();
        MatchFixtures.loaded(matches, token);
    }

    MatchSnapshot snapshot() {
        return matches.current(token).orElseThrow();
    }

    UUID active() {
        return snapshot().participants().stream().filter(p -> p.slotIndex() == snapshot().turn().activeSlot())
            .findFirst().orElseThrow().sessionId();
    }

    RoomMatchContext context(UUID id) {
        return room.scenario.rooms.matchContext(id);
    }

    ScenarioApplicationResult shot(int damage, boolean suicide, boolean fall) throws Exception {
        var snapshot = snapshot();
        var shooter = active();
        var actor = snapshot.actors().get(shooter);
        var source = MatchScenarioTestPackets.shot(snapshot.turn().activeSlot(), actor.facing(), damage, fall);
        var scene = source.scenario();
        var events = new ArrayList<ScenarioCommand>(scene.commands());

        if (suicide) {
            events.add(new ScenarioDamageCommand(
                new ScenarioCommandHeader(snapshot.turn().activeSlot(), 4, 995, 0),
                10000,
                0,
                0,
                0
            ));
            events.sort(Comparator.comparingInt(event -> event.header().tick()));
        }
        var packet = new PcGamePlayScenario(
            source.facing(),
            source.shooterSlot(),
            new GameScenario(
                scene.slot(),
                scene.scalar09(),
                events,
                scene.selectedItems(),
                scene.effects(),
                scene.flags(),
                scene.power(),
                scene.refittedSlots(),
                scene.scalar0c()
            ),
            source.scalar18(),
            source.scalar1c(),
            source.tail()
        );
        return matches.applyScenario(new ApplyScenarioCommand(
            MatchCommandContext.from(snapshot, shooter),
            snapshot.turn().revision(),
            snapshot.world().revision(),
            new ScenarioClaimsMapper().map(packet),
            new ScenarioCodec().encode(packet),
            snapshot.phaseRevision()
        )).toCompletableFuture().join();
    }

    ConfirmScenarioCommand ackCommand(UUID id) {
        var snapshot = snapshot();
        var pending = snapshot.completion().orElseThrow();
        return new ConfirmScenarioCommand(
            MatchCommandContext.from(snapshot, id),
            snapshot.turn().revision(),
            snapshot.world().revision(),
            Optional.of(pending.fingerprint()),
            snapshot.turn().activeSlot(),
            snapshot.phaseRevision()
        );
    }

    void ack(UUID id) {
        matches.confirmScenario(ackCommand(id)).toCompletableFuture().join();
    }

    CompletionStage<RoomMatchTerminationResult> surrender(UUID id) {
        return new TerminateMatchAction(matches).execute(id, context(id));
    }

    void disconnect(UUID id) {
        matches.unsubscribe(id);
        room.scenario.rooms.release(id, "PEER_CLOSED");
    }

    @Override
    public void close() {
        room.close();
    }
}
