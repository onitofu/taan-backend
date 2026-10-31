package taan.online.match.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import taan.online.room.model.CaptainSelectionResult;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;

public final class Match {
    private final MatchId id;
    private final RoomId roomId;
    private final UUID roomGeneration;
    private final int channelId;
    private final UUID ownerSessionId;
    private final RoomMode mode;
    private final RoomRule rule;
    private final MatchDefinitions definitions;
    private final MatchBoxPolicy boxPolicy;
    private final List<MatchParticipant> participants;
    private final CaptainSelectionResult captains;
    private MatchTurn turn;
    private MatchWorldState world;
    private MatchResolutionState resolution = MatchResolutionState.initial();
    private MatchParticipationState participation = MatchParticipationState.initial();
    private MatchLootState loot = MatchLootState.empty();
    private final Map<UUID, MatchAttackSelection> attacks = new HashMap<>();
    private final Map<UUID, MatchMovementState> movements = new HashMap<>();
    private Optional<MatchTurnClosure> closure = Optional.empty();
    private Optional<MatchCompletion> completion = Optional.empty();
    private final Set<UUID> loaded = new HashSet<>();
    private final Set<UUID> delivered = new HashSet<>();
    private final MatchStateMachine lifecycle = new MatchStateMachine();

    public Match(
        MatchId id,
        RoomId roomId,
        UUID roomGeneration,
        int channelId,
        UUID ownerSessionId,
        RoomMode mode,
        RoomRule rule,
        MatchDefinitions definitions,
        List<MatchParticipant> participants,
        CaptainSelectionResult captains,
        int firstTurnSlot,
        MatchBoxPolicy boxPolicy
    ) {
        this.id = Objects.requireNonNull(id);
        this.roomId = Objects.requireNonNull(roomId);
        this.roomGeneration = Objects.requireNonNull(roomGeneration);
        this.channelId = channelId;
        this.ownerSessionId = Objects.requireNonNull(ownerSessionId);
        this.mode = Objects.requireNonNull(mode);
        this.rule = Objects.requireNonNull(rule);
        this.definitions = Objects.requireNonNull(definitions);
        this.boxPolicy = Objects.requireNonNull(boxPolicy);
        this.participants = List.copyOf(participants);
        this.captains = captains;
        turn = MatchTurnPolicy.roster(this, firstTurnSlot);
        world = MatchWorldState.initial(participants, definitions.map().terrainLayers());
        turn = turn.withBoxAttemptBudget(MatchBoxRules.budget(boxPolicy, world.boxes()));
    }

    public Match(
        MatchId id,
        RoomId roomId,
        UUID roomGeneration,
        int channelId,
        UUID ownerSessionId,
        RoomMode mode,
        RoomRule rule,
        MatchDefinitions definitions,
        List<MatchParticipant> participants,
        CaptainSelectionResult captains,
        MatchTurn initialTurn,
        MatchBoxPolicy boxPolicy
    ) {
        this(
            id,
            roomId,
            roomGeneration,
            channelId,
            ownerSessionId,
            mode,
            rule,
            definitions,
            participants,
            captains,
            initialTurn.activeSlot(),
            boxPolicy
        );
        turn = initialTurn.withBoxAttemptBudget(MatchBoxRules.budget(boxPolicy, world.boxes()));
    }

    public Optional<MatchCompletion> completion() {
        return completion;
    }

    public boolean acknowledgeScenario(UUID session) {
        if (phase() != MatchPhase.COMPLETION || completion.isEmpty()
            || !completion.orElseThrow().awaitingSessions().contains(session)) {
            return false;
        }

        completion = Optional.of(completion.orElseThrow().acknowledged(session));
        return true;
    }

    public boolean nextTurn(long now, long timeoutNanos) {
        if (phase() != MatchPhase.SETTLING && (phase() != MatchPhase.COMPLETION || completion.isEmpty()
            || !completion.orElseThrow().awaitingSessions().isEmpty()
            || now - completion.orElseThrow().settleNotBeforeNanos() < 0)) {
            return false;
        }

        if (phase() == MatchPhase.SETTLING && now - settleAt() < 0) {
            return false;
        }

        var alive = participants.stream().filter(p -> world.actors().get(p.sessionId()).hp() > 0
            && !resolution.exits().containsKey(p.sessionId()))
            .map(MatchParticipant::slotIndex).collect(Collectors.toSet());
        var next = new MatchTurnPolicy().next(turn, alive, now);
        turn = next.withBoxAttemptBudget(MatchBoxRules.budget(boxPolicy, world.boxes()));
        var member = participants.stream().filter(p -> p.slotIndex() == next.activeSlot()).findFirst().orElseThrow();
        loot = loot.closeQuiz(world).expired(member.sessionId());
        world = world.grantEnergy(member, next.energyGrant());
        completion = Optional.empty();
        lifecycle.on(MatchLifecycleEvent.NEXT_TURN, OptionalLong.of(now + timeoutNanos));
        closure = Optional.empty();
        startMovement(now);
        return true;
    }

    public MatchResolutionState resolution() {
        return resolution;
    }

    public MatchLootState loot() {
        return loot;
    }

    public void updateLoot(MatchLootState value) {
        loot = Objects.requireNonNull(value);
    }

    public void withdraw(UUID session, MatchExitKind kind) {
        loot = loot.without(session);
        attacks.remove(session);
        resolution = resolution.exit(session, kind, participants, world, turn.activeSlot(), captains);

        if (phase() == MatchPhase.ACTION && resolution.pendingOutcome().isEmpty()) {
            movements.computeIfPresent(
                session,
                (id, value) -> new MatchMovementState(value.allowance(), Optional.empty())
            );
        }

        completion = completion.map(value -> value.acknowledged(session));
    }

    public void exclude(Set<UUID> sessions) {
        sessions.forEach(session -> loot = loot.without(session));
        attacks.keySet().removeAll(sessions);
        resolution = resolution.exclude(sessions, participants, world, turn.activeSlot(), captains);
        sessions.forEach(session -> completion = completion.map(value -> value.acknowledged(session)));
    }

    public Map<UUID, MatchAttackSelection> attacks() {
        return Map.copyOf(attacks);
    }

    public boolean useItem(UUID session, int index, int itemId) {
        if (phase() != MatchPhase.ACTION || !activeSession().equals(session) || world.actors().get(session).dead()) {
            return false;
        }

        var member = participants.stream().filter(value -> value.sessionId().equals(session)).findFirst().orElseThrow();
        var activation = MatchItemSelectionRules.activate(
            member,
            world,
            loot,
            attacks.getOrDefault(session, MatchAttackSelection.NORMAL),
            index,
            itemId
        );

        if (activation.isEmpty()) {
            return false;
        }

        var applied = activation.orElseThrow();
        world = applied.world();
        loot = applied.loot();
        attacks.put(session, applied.selection());
        return true;
    }

    public MatchId id() {
        return id;
    }

    public RoomId roomId() {
        return roomId;
    }

    public UUID roomGeneration() {
        return roomGeneration;
    }

    public int channelId() {
        return channelId;
    }

    public UUID ownerSessionId() {
        return ownerSessionId;
    }

    public RoomMode mode() {
        return mode;
    }

    public RoomRule rule() {
        return rule;
    }

    public MatchDefinitions definitions() {
        return definitions;
    }

    public List<MatchParticipant> participants() {
        return participants;
    }

    public CaptainSelectionResult captains() {
        return captains;
    }

    public MatchPhase phase() {
        return lifecycle.phase();
    }

    public Set<UUID> loadedSessions() {
        return Set.copyOf(loaded);
    }

    public Map<UUID, MatchActorState> actors() {
        return world.actors();
    }

    public MatchWorldState world() {
        return world;
    }

    public boolean commitScenario(
        long expectedWorldRevision,
        long expectedTurnRevision,
        MatchWorldState prepared,
        Optional<MatchCompletion> pending,
        MatchLootState expectedLoot,
        MatchLootState preparedLoot
    ) {
        if (loot != expectedLoot || world.revision() != expectedWorldRevision || turn.revision() != expectedTurnRevision
            || !MatchScenarioCommitRules.permits(phase(), world, turn, prepared, participants)) {
            return false;
        }

        var active = activeSession();
        movements.put(active, MatchScenarioCommitRules.movement(world, prepared, active, movements.get(active)));
        attacks.remove(activeSession());
        attacks.keySet().removeIf(session -> prepared.actors().get(session).dead());
        loot = rule == RoomRule.POWER_QUIZ
            ? preparedLoot.openQuiz(participants, world, resolution, turn, prepared) : preparedLoot;
        world = prepared;
        resolution = resolution.evaluate(participants, world, turn.activeSlot(), captains);
        completion = pending;
        lifecycle.on(
            MatchLifecycleEvent.SCENARIO_ACCEPTED,
            pending.isPresent() ? OptionalLong.of(pending.orElseThrow().deadlineNanos()) : OptionalLong.empty()
        );
        return true;
    }

    public boolean actionStarted(long now, long timeoutNanos) {
        if (phase() != MatchPhase.SYNCHRONIZING || !MatchLoadingRules.delivered(this, delivered)) {
            return false;
        }

        lifecycle.on(MatchLifecycleEvent.START_ACTION, OptionalLong.of(now + timeoutNanos));
        startMovement(now);
        delivered.clear();
        return true;
    }

    public MatchTurn turn() {
        return turn;
    }

    public long phaseRevision() {
        return lifecycle.revision();
    }

    public long deadlineNanos() {
        return lifecycle.deadlineNanos();
    }

    public boolean expired(long now) {
        return phase() != MatchPhase.RESERVED && now - deadlineNanos() >= 0;
    }

    public boolean commit(long now, long timeoutNanos) {
        if (phase() != MatchPhase.RESERVED) {
            return false;
        }

        lifecycle.on(MatchLifecycleEvent.START, OptionalLong.of(now + timeoutNanos));
        return true;
    }

    public MatchLoadingOutcome loadingDone(UUID session, int slot, int x, int y) {
        var result = MatchLoadingRules.validate(this, session, slot, x, y);

        if (result != MatchLoadingOutcome.ACCEPTED) {
            return result;
        }

        var actor = participants.stream().filter(p -> p.sessionId().equals(session)).findFirst().orElseThrow();
        world = world.placed(session, new MatchActorState(x, y, actor.facing(), actor.hp(), actor.energy()));
        loaded.add(session);
        return MatchLoadingOutcome.ACCEPTED;
    }

    public Set<UUID> loadingReadySessions() {
        return loaded.stream().filter(delivered::contains).collect(Collectors.toUnmodifiableSet());
    }

    public boolean deliveryCompleted(UUID session) {
        if (phase() != MatchPhase.LOADING && phase() != MatchPhase.SYNCHRONIZING
            || participants.stream().noneMatch(value -> value.sessionId().equals(session))) {
            return false;
        }

        return delivered.add(session);
    }

    public boolean beginSynchronization(long now) {
        return beginSynchronization(now, 0);
    }

    public boolean beginSynchronization(long now, long deliveryTimeoutNanos) {
        if (phase() != MatchPhase.LOADING || !MatchLoadingRules.ready(this, delivered)) {
            return false;
        }

        turn = MatchLoadingRules.turn(this, now);
        lifecycle.on(
            MatchLifecycleEvent.LOADING_READY,
            OptionalLong.of(Math.max(deadlineNanos(), now + deliveryTimeoutNanos))
        );
        delivered.clear();
        return true;
    }

    public boolean previewStarted(long now, long timeoutNanos) {
        if (phase() != MatchPhase.SYNCHRONIZING || !MatchLoadingRules.delivered(this, delivered)) {
            return false;
        }

        lifecycle.on(MatchLifecycleEvent.START_PREVIEW, OptionalLong.of(now + timeoutNanos));
        delivered.clear();
        return true;
    }

    public boolean confirmsPosition(UUID session, int slot, int x, int y, int facing) {
        if (phase() != MatchPhase.SYNCHRONIZING && phase() != MatchPhase.PREVIEW && phase() != MatchPhase.ACTION) {
            return false;
        }

        var actor = participants.stream().filter(value -> value.sessionId().equals(session)).findFirst().orElse(null);
        var placement = world.actors().get(session);
        return actor != null && actor.slotIndex() == slot && placement.facing() == facing
            && placement.x() == x && placement.y() == y;
    }

    public Map<UUID, MatchMovementState> movements() {
        return Map.copyOf(movements);
    }

    public Optional<MatchTurnClosure> closure() {
        return closure;
    }

    private UUID activeSession() {
        return participants.stream().filter(p -> p.slotIndex() == turn.activeSlot())
            .findFirst().orElseThrow().sessionId();
    }

    private void startMovement(long now) {
        participation = participation.start(activeSession(), now);
        int weapon = participants.stream().filter(p -> p.sessionId().equals(activeSession()))
            .findFirst().orElseThrow().weaponId();
        int budget = definitions.weapons().get(weapon).resourceMove();
        movements.put(activeSession(), MatchMovementState.initial(turn.revision(), now, budget));
    }

    public void move(UUID session, int x, int y, int facing, long now) {
        var actor = world.actors().get(session);
        var next = MatchMovementRules.admit(this, session, x, y, facing, now);

        if (actor.x() != x || actor.y() != y || actor.facing() != facing) {
            world = world.placed(session, new MatchActorState(x, y, facing, actor.hp(), actor.energy()));
        }

        movements.put(session, next);
    }

    public MatchParticipationState participation() {
        return participation;
    }

    public void updateParticipation(MatchParticipationState value) {
        participation = Objects.requireNonNull(value);
    }

    public void closeTurn(MatchTurnClosureReason reason, long now) {
        participation = participation.finish(now, reason == MatchTurnClosureReason.PASS_TURN);
        long drain = reason == MatchTurnClosureReason.ACTION_TIMEOUT ? now + 500000000L : now;
        long landing = reason == MatchTurnClosureReason.ACTION_TIMEOUT
            ? drain + (3L * (definitions.map().height() + 200) + 3) * 1000000L
            : movements.get(activeSession()).fall().map(MatchWalkingFall::endsAtNanos).orElse(now);
        closure = Optional.of(new MatchTurnClosure(reason, turn.revision(), phaseRevision(), drain, landing));
        lifecycle.on(MatchLifecycleEvent.CLOSE_TURN, OptionalLong.of(settleAt()));
    }

    public long settleAt() {
        long fall = movements.get(activeSession()).fall().map(MatchWalkingFall::endsAtNanos)
            .orElse(closure.orElseThrow().stopDrainUntilNanos());
        return Math.max(closure.orElseThrow().stopDrainUntilNanos(), fall);
    }

    public void settleMovement(long now) {
        var session = activeSession();
        var movement = movements.get(session);

        if (movement.fall().isEmpty() || now - movement.fall().orElseThrow().endsAtNanos() < 0) {
            return;
        }

        world = world.settled(session, movement.fall().orElseThrow().endY(), definitions.map().height());
        loot = new ScenarioLootDraft(loot).finish(world);
        attacks.keySet().removeIf(id -> world.actors().get(id).dead());
        resolution = resolution.settled(participants, world, turn.activeSlot(), captains);
        movements.put(session, new MatchMovementState(movement.allowance(), Optional.empty()));
    }

    public MatchLifecycleEvent lastLifecycleEvent() {
        return lifecycle.lastEvent();
    }

    public MatchLifecycleTransition transition(MatchLifecycleEvent event) {
        var transition = lifecycle.on(event, OptionalLong.empty());

        if (transition.effects().contains(MatchLifecycleEffect.STOP_GAMEPLAY)) {
            loot = MatchLootState.empty();
            world = world.withoutBoxes();
        }

        if (transition.effects().contains(MatchLifecycleEffect.CLEANUP)) {
            attacks.clear();
            world = world.withoutBoxes();
            loot = MatchLootState.empty();
            resolution = MatchResolutionState.initial();
        }

        return transition;
    }

    public boolean beginTermination() {
        return transition(MatchLifecycleEvent.TERMINATE).decision() == MatchTransitionDecision.APPLIED;
    }

    public boolean cancel() {
        return transition(MatchLifecycleEvent.CLOSE).decision() == MatchTransitionDecision.APPLIED;
    }
}
