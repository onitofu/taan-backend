package taan.online.match.model;

import java.util.HashSet;
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
        CaptainSelectionResult captains
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

        if (!sessions.contains(ownerSessionId) || teams.size() != 2
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

    public boolean commit() {
        if (phase != MatchPhase.RESERVED) {
            return false;
        }

        phase = MatchPhase.LOADING;
        return true;
    }

    public boolean previewStarted() {
        if (phase != MatchPhase.LOADING) {
            return false;
        }

        phase = MatchPhase.PREVIEW;
        return true;
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
