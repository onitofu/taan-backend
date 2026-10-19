package taan.online.match.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import taan.online.room.model.CaptainSelectionResult;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomTeam;

public final class Match {
    private final MatchId id;
    private final RoomId roomId;
    private final UUID roomGeneration;
    private final int channelId;
    private final UUID ownerSessionId;
    private final RoomMode mode;
    private final RoomRule rule;
    private final MatchDefinitions definitions;
    private final List<MatchParticipant> participants;
    private final CaptainSelectionResult captains;
    private MatchTurn turn;
    private MatchWorldState world;
    private MatchResolutionState resolution = MatchResolutionState.initial();
    private final Map<UUID, MatchAttackSelection> attacks = new HashMap<>();
    private final Map<UUID, MatchMovementState> movements = new HashMap<>();
    private Optional<MatchTurnClosure> closure = Optional.empty();
    private Optional<MatchCompletion> completion = Optional.empty();
    private final Set<UUID> loaded = new HashSet<>();
    private final Set<UUID> delivered = new HashSet<>();
    private long phaseRevision;
    private long deadlineNanos;
    private MatchPhase phase = MatchPhase.RESERVED;

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
        int firstTurnSlot
    ) {
        this.id = Objects.requireNonNull(id);
        this.roomId = Objects.requireNonNull(roomId);
        this.roomGeneration = Objects.requireNonNull(roomGeneration);
        this.channelId = channelId;
        this.ownerSessionId = Objects.requireNonNull(ownerSessionId);
        this.mode = Objects.requireNonNull(mode);
        this.rule = Objects.requireNonNull(rule);
        this.definitions = Objects.requireNonNull(definitions);
        this.participants = List.copyOf(participants);
        this.captains = captains;
        turn = MatchTurnPolicy.roster(
            channelId,
            participants,
            mode,
            rule,
            ownerSessionId,
            captains,
            definitions,
            firstTurnSlot
        );
        world = MatchWorldState.initial(participants, definitions.map().terrainLayers());
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
        MatchTurn initialTurn
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
            initialTurn.activeSlot()
        );
        turn = initialTurn;
    }

    public Optional<MatchCompletion> completion() {
        return completion;
    }

    public boolean acknowledgeScenario(UUID session) {
        if (phase != MatchPhase.COMPLETION || completion.isEmpty()
            || !completion.orElseThrow().awaitingSessions().contains(session)) {
            return false;
        }

        completion = Optional.of(completion.orElseThrow().acknowledged(session));
        return true;
    }

    public boolean nextTurn(long now, long timeoutNanos) {
        if (phase != MatchPhase.SETTLING && (phase != MatchPhase.COMPLETION || completion.isEmpty()
            || !completion.orElseThrow().awaitingSessions().isEmpty()
            || now - completion.orElseThrow().settleNotBeforeNanos() < 0)) {
            return false;
        }

        if (phase == MatchPhase.SETTLING && now - settleAt() < 0) {
            return false;
        }

        var alive = participants.stream().filter(p -> world.actors().get(p.sessionId()).hp() > 0)
            .map(MatchParticipant::slotIndex).collect(Collectors.toSet());
        var next = new MatchTurnPolicy().next(turn, alive, now);
        turn = next;
        var member = participants.stream().filter(p -> p.slotIndex() == next.activeSlot()).findFirst().orElseThrow();
        int energy = MatchEnergyRules.adjust(
            actors().get(member.sessionId()).energy(),
            next.energyGrant(),
            member.maxEnergy()
        );
        world = world.energy(member.sessionId(), energy);
        completion = Optional.empty();
        phase = MatchPhase.ACTION;
        phaseRevision++;
        deadlineNanos = now + timeoutNanos;
        closure = Optional.empty();
        startMovement(now);
        return true;
    }

    public MatchResolutionState resolution() {
        return resolution;
    }

    public void withdraw(UUID session, MatchExitKind kind) {
        resolution = resolution.exit(session, kind, participants, world, turn.activeSlot());
        completion = completion.map(value -> value.acknowledged(session));
    }

    public Map<UUID, MatchAttackSelection> attacks() {
        return Map.copyOf(attacks);
    }

    public boolean activateSpecial(UUID session) {
        var actor = world.actors().get(session);

        if (phase != MatchPhase.ACTION || !activeSession().equals(session) || actor.dead()
            || actor.energy() != participants.stream().filter(p -> p.sessionId().equals(session))
                .findFirst().orElseThrow().maxEnergy()) {
            return false;
        }

        world = world.energy(session, 0);
        attacks.put(session, MatchAttackSelection.SPECIAL);
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
        return phase;
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

    public boolean commitScenario(long expectedWorldRevision, long expectedTurnRevision, MatchWorldState prepared) {
        return commitScenario(expectedWorldRevision, expectedTurnRevision, prepared, Optional.empty());
    }

    public boolean commitScenario(
        long expectedWorldRevision,
        long expectedTurnRevision,
        MatchWorldState prepared,
        Optional<MatchCompletion> pending
    ) {
        if (phase != MatchPhase.ACTION || world.revision() != expectedWorldRevision
            || turn.revision() != expectedTurnRevision || prepared.revision() != expectedWorldRevision + 1
            || prepared.receipt().filter(
                value -> value.worldRevision() == prepared.revision() && value.turnRevision() == turn.revision()
                    && participants.stream().anyMatch(
                        member -> member.sessionId().equals(value.sessionId())
                            && member.admissionId().equals(value.admissionId())
                    )
            ).isEmpty()) {
            return false;
        }

        var moving = movements.get(activeSession());
        var actor = world.actors().get(activeSession());
        var applied = prepared.actors().get(activeSession());

        if (prepared.falls().containsKey(activeSession()) || actor.y() != applied.y()
            || applied.dead()) {
            movements.put(activeSession(), new MatchMovementState(moving.allowance(), Optional.empty()));
        }

        attacks.remove(activeSession());
        world = prepared;
        resolution = resolution.evaluate(participants, world, turn.activeSlot());
        completion = pending;
        pending.ifPresent(value -> deadlineNanos = value.deadlineNanos());
        phase = MatchPhase.COMPLETION;
        phaseRevision++;
        return true;
    }

    public boolean actionStarted(long now, long timeoutNanos) {
        if (phase != MatchPhase.SYNCHRONIZING || delivered.size() != participants.size()) {
            return false;
        }

        phase = MatchPhase.ACTION;
        phaseRevision++;
        deadlineNanos = now + timeoutNanos;
        startMovement(now);
        delivered.clear();
        return true;
    }

    public MatchTurn turn() {
        return turn;
    }

    public long phaseRevision() {
        return phaseRevision;
    }

    public long deadlineNanos() {
        return deadlineNanos;
    }

    public boolean expired(long now) {
        return phase != MatchPhase.RESERVED && now - deadlineNanos >= 0;
    }

    public boolean commit(long now, long timeoutNanos) {
        if (phase != MatchPhase.RESERVED) {
            return false;
        }

        phase = MatchPhase.LOADING;
        phaseRevision++;
        deadlineNanos = now + timeoutNanos;
        return true;
    }

    public String loadingDone(UUID session, int slot, int x, int y) {
        var actor = participants.stream().filter(value -> value.sessionId().equals(session)).findFirst().orElse(null);

        if (actor == null || actor.slotIndex() != slot) {
            return "FOREIGN_SLOT";
        }

        var accepted = loaded.contains(session) ? world.actors().get(session) : null;

        if (accepted != null) {
            return accepted.x() == x && accepted.y() == y ? "UNCHANGED" : "CONFLICTING_POSITION";
        }

        if (phase != MatchPhase.LOADING) {
            return "INVALID_PHASE";
        }

        int side = actor.team() == RoomTeam.BLUE ? 1 : 2;
        var placement = definitions.map().spawns().stream()
            .filter(value -> value.valid() && (value.side() == 0 || value.side() == side))
            .filter(value -> value.settledX() == x && value.settledY() == y).findFirst().orElse(null);

        if (placement == null) {
            return "INVALID_POSITION";
        }

        world = world.placed(session, new MatchActorState(x, y, actor.facing(), actor.hp(), actor.energy()));
        loaded.add(session);
        return "ACCEPTED";
    }

    public boolean deliveryCompleted(UUID session) {
        if (phase != MatchPhase.LOADING && phase != MatchPhase.SYNCHRONIZING
            || participants.stream().noneMatch(value -> value.sessionId().equals(session))) {
            return false;
        }

        return delivered.add(session);
    }

    public boolean beginSynchronization(long now) {
        if (phase != MatchPhase.LOADING || loaded.size() != participants.size()
            || delivered.size() != participants.size()) {
            return false;
        }

        turn = turn.issuedAt(now);
        phase = MatchPhase.SYNCHRONIZING;
        phaseRevision++;
        delivered.clear();
        return true;
    }

    public boolean previewStarted(long now, long timeoutNanos) {
        if (phase != MatchPhase.SYNCHRONIZING || delivered.size() != participants.size()) {
            return false;
        }

        phase = MatchPhase.PREVIEW;
        phaseRevision++;
        deadlineNanos = now + timeoutNanos;
        delivered.clear();
        return true;
    }

    public boolean confirmsPosition(UUID session, int slot, int x, int y, int facing) {
        if (phase != MatchPhase.SYNCHRONIZING && phase != MatchPhase.PREVIEW && phase != MatchPhase.ACTION) {
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
        int budget = definitions.weapons().getFirst().resourceMove();
        movements.put(
            activeSession(),
            new MatchMovementState(
                new MovementAllowance(turn.revision(), now, now, budget, 0, 20),
                Optional.empty()
            )
        );
    }

    public void move(UUID session, int x, int y, int facing, long now) {
        var actor = world.actors().get(session);
        var next = MatchMovementRules.admit(
            movements.get(session),
            actor,
            x,
            y,
            facing,
            definitions.map(),
            world.terrain(),
            now
        );

        if (closure.isPresent() && next.fall().isPresent()
            && next.fall().orElseThrow().endsAtNanos() - closure.orElseThrow().landingDeadlineNanos() > 0) {
            throw new ScenarioWorldException("MOVEMENT_LANDING_DEADLINE_EXCEEDED");
        }

        if (actor.x() != x || actor.y() != y || actor.facing() != facing) {
            world = world.placed(session, new MatchActorState(x, y, facing, actor.hp(), actor.energy()));
        }

        movements.put(session, next);
    }

    public void closeTurn(MatchTurnClosureReason reason, long now) {
        long drain = reason == MatchTurnClosureReason.ACTION_TIMEOUT ? now + 500000000L : now;
        long landing = reason == MatchTurnClosureReason.ACTION_TIMEOUT
            ? drain + (3L * (definitions.map().height() + 200) + 3) * 1000000L
            : movements.get(activeSession()).fall().map(MatchWalkingFall::endsAtNanos).orElse(now);
        closure = Optional.of(new MatchTurnClosure(reason, turn.revision(), phaseRevision, drain, landing));
        phase = MatchPhase.SETTLING;
        phaseRevision++;
        deadlineNanos = settleAt();
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
        resolution = resolution.settled(participants, world, turn.activeSlot());
        movements.put(session, new MatchMovementState(movement.allowance(), Optional.empty()));
    }

    public boolean beginTermination() {
        if (phase == MatchPhase.CANCELLED || phase == MatchPhase.TERMINATING) {
            return false;
        }

        phase = MatchPhase.TERMINATING;
        return true;
    }

    public boolean cancel() {
        if (phase == MatchPhase.CANCELLED) {
            return false;
        }

        attacks.clear();
        resolution = MatchResolutionState.initial();
        phase = MatchPhase.CANCELLED;
        return true;
    }
}
