package taan.online.match.service;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import taan.online.match.model.Match;
import taan.online.match.model.MatchActorState;
import taan.online.match.model.MatchItemBox;
import taan.online.match.model.MatchTurnClosureReason;
import taan.online.match.model.MatchWorldState;
import taan.online.match.service.model.ConfirmMatchGhostPositionCommand;
import taan.online.match.service.model.MakeMatchGhostCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.MoveMatchActorCommand;
import taan.online.match.service.model.ReportMatchPickupCommand;
import taan.online.match.service.model.SetMatchGhostTargetCommand;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;

final class GhostMatchFixture implements AutoCloseable {
    static final long BOX = 0x0500000000000001L;
    final NoItemFixture scenario;
    final MatchRuntimeFixture f;

    GhostMatchFixture(RoomMode mode) throws Exception {
        this(mode, MatchLootPolicy.nativeItems());
    }

    GhostMatchFixture(RoomMode mode, MatchLootPolicy policy) throws Exception {
        this(mode, policy, mode.usesItems() ? RoomRule.BONUS : RoomRule.BASIC);
    }

    GhostMatchFixture(RoomMode mode, MatchLootPolicy policy, RoomRule rule) throws Exception {
        scenario = new NoItemFixture(mode, rule, 4, policy);
        f = scenario.f;
    }

    MatchSnapshot snapshot() {
        return f.runtime.snapshot();
    }

    MatchCommandContext context(int slot) {
        return scenario.context(slot);
    }

    void kill(int slot, int x, int y) throws Exception {
        var session = context(slot).sessionId();
        world(snapshot().world().placed(session, new MatchActorState(x, y, 0, 0, 0)));
    }

    void box(long id, int x, int y) throws Exception {
        var before = snapshot().world();
        var boxes = new HashMap<>(before.boxes());
        boxes.put(id, new MatchItemBox(id, x, y, true));
        world(new MatchWorldState(
            before.revision(),
            before.actors(),
            before.terrain(),
            before.falls(),
            before.processedDeaths(),
            before.receipt(),
            boxes,
            before.combatStatistics(),
            before.teamGauges()
        ));
    }

    void world(MatchWorldState value) throws Exception {
        var field = Match.class.getDeclaredField("world");
        field.setAccessible(true);
        field.set(f.match, value);
        f.runtime.refresh();
    }

    boolean make(int slot) {
        return complete(f.runtime.turnWork(
            () -> f.runtime.ghosts.make(new MakeMatchGhostCommand(context(slot), slot))
        ));
    }

    boolean target(int slot, int x, int y) {
        return complete(f.runtime.turnWork(() -> f.runtime.ghosts.target(
            new SetMatchGhostTargetCommand(context(slot), slot, x, y),
            f.timers.now
        )));
    }

    boolean position(int slot, int x, int y, int facing) {
        return complete(f.runtime.turnWork(() -> f.runtime.ghosts.position(
            new ConfirmMatchGhostPositionCommand(context(slot), slot, x, y, facing),
            f.timers.now
        )));
    }

    boolean fall(int y) {
        var snapshot = snapshot();
        int slot = snapshot.turn().activeSlot();
        var context = context(slot);
        var actor = snapshot.actors().get(context.sessionId());
        var command = new MoveMatchActorCommand(
            context,
            snapshot.turn().revision(),
            snapshot.phaseRevision(),
            slot,
            actor.x(),
            y,
            actor.facing(),
            MatchMovementKind.MOVE
        );
        return complete(f.runtime.turnWork(() -> f.runtime.movement.move(command)));
    }

    void nextTurn() {
        f.match.closeTurn(MatchTurnClosureReason.PASS_TURN, f.timers.now);
        f.match.nextTurn(f.timers.now, 20_000_000_000L);
        f.runtime.refresh();
    }

    ReportMatchPickupCommand pickup(int slot, long id, int item) {
        return new ReportMatchPickupCommand(context(slot), slot, slot, Optional.empty(), id, item);
    }

    boolean pickup(ReportMatchPickupCommand command) {
        return complete(MatchItemExecution.reportPickup(f.runtime, command));
    }

    boolean complete(CompletionStage<Boolean> result) {
        f.run();
        return result.toCompletableFuture().join();
    }

    @Override
    public void close() {
        scenario.close();
    }
}
