package taan.online.match.model;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
    private final int firstTurnSlot;
    private final Map<UUID, MapSpawnDefinition> placements = new HashMap<>();
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
        this.firstTurnSlot = firstTurnSlot;
        validate();
    }

    private void validate() {
        if (channelId < 1 || channelId > 65535 || participants.size() < 2 || participants.size() > 8
            || mode.usesItems() != (rule == RoomRule.BONUS)) {
            throw new IllegalArgumentException("Invalid match setup");
        }

        var sessions = new HashSet<UUID>();
        var admissions = new HashSet<UUID>();
        var accounts = new HashSet<Long>();
        var characters = new HashSet<Long>();
        var slots = new HashSet<Integer>();
        var spawns = new HashSet<Integer>();
        var teams = new HashSet<RoomTeam>();

        for (var participant : participants) {
            if (!sessions.add(participant.sessionId()) || !admissions.add(participant.admissionId())
                || !accounts.add(participant.character().accountId().value())
                || !characters.add(participant.character().id().value()) || !slots.add(participant.slotIndex())
                || !spawns.add(participant.spawn().index())
                || !definitions.map().spawns().contains(participant.spawn())) {
                throw new IllegalArgumentException("Invalid match roster");
            }

            teams.add(participant.team());
        }

        if (!slots.contains(firstTurnSlot) || !sessions.contains(ownerSessionId) || teams.size() != 2
            || mode.requiresCaptainSelection() != (captains != null)) {
            throw new IllegalArgumentException("Invalid match ownership or teams");
        }

        if (captains != null
            && (!slots.contains(captains.blueLeaderSlot()) || !slots.contains(captains.redLeaderSlot()))) {
            throw new IllegalArgumentException("Invalid match captains");
        }
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

    public int firstTurnSlot() {
        return firstTurnSlot;
    }

    public int initialWindRaw() {
        return 0;
    }

    public Map<UUID, MapSpawnDefinition> placements() {
        return Map.copyOf(placements);
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

        var accepted = placements.get(session);

        if (accepted != null) {
            return accepted.settledX() == x && accepted.settledY() == y ? "UNCHANGED" : "CONFLICTING_POSITION";
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

        placements.put(session, placement);
        return "ACCEPTED";
    }

    public boolean deliveryCompleted(UUID session) {
        if (phase != MatchPhase.LOADING && phase != MatchPhase.SYNCHRONIZING
            || participants.stream().noneMatch(value -> value.sessionId().equals(session))) {
            return false;
        }

        return delivered.add(session);
    }

    public boolean beginSynchronization() {
        if (phase != MatchPhase.LOADING || placements.size() != participants.size()
            || delivered.size() != participants.size()) {
            return false;
        }

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
        if (phase != MatchPhase.SYNCHRONIZING && phase != MatchPhase.PREVIEW) {
            return false;
        }

        var actor = participants.stream().filter(value -> value.sessionId().equals(session)).findFirst().orElse(null);
        var placement = placements.get(session);
        return actor != null && actor.slotIndex() == slot && actor.facing() == facing
            && placement.settledX() == x && placement.settledY() == y;
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

        phase = MatchPhase.CANCELLED;
        return true;
    }
}
