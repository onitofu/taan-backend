package taan.online.match.service.model;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import taan.online.match.model.MatchCompletion;
import taan.online.match.model.MatchMovementState;
import taan.online.match.model.MatchTurnClosure;
import java.util.Set;
import java.util.UUID;
import taan.online.match.model.Match;
import taan.online.match.model.MatchAttackSelection;
import taan.online.match.model.MatchActorState;
import taan.online.match.model.MatchDefinitions;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.MatchTurn;
import taan.online.match.model.MatchWorldState;
import taan.online.room.model.CaptainSelectionResult;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;

public record MatchSnapshot(
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
    MatchPhase phase,
    Set<UUID> loadedSessions,
    MatchWorldState world,
    MatchTurn turn,
    long phaseRevision,
    long deadlineNanos,
    Optional<MatchCompletion> completion,
    Map<UUID, MatchMovementState> movements,
    Optional<MatchTurnClosure> closure,
    Map<UUID, MatchAttackSelection> attacks
) {
    public MatchSnapshot(
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
        MatchPhase phase,
        Set<UUID> loadedSessions,
        MatchWorldState world,
        MatchTurn turn,
        long phaseRevision,
        long deadlineNanos
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
            phase,
            loadedSessions,
            world,
            turn,
            phaseRevision,
            deadlineNanos,
            Optional.empty(),
            Map.of(),
            Optional.empty(),
            Map.of()
        );
    }

    public MatchSnapshot {
        participants = List.copyOf(participants);
        loadedSessions = Set.copyOf(loadedSessions);
        movements = Map.copyOf(movements);
        attacks = Map.copyOf(attacks);
    }

    public Map<UUID, MatchActorState> actors() {
        return world.actors();
    }

    public MatchSnapshot completed(MatchWorldState applied) {
        return completed(applied, Optional.empty());
    }

    public MatchSnapshot completed(MatchWorldState applied, Optional<MatchCompletion> pending) {
        var remaining = new HashMap<>(attacks);
        participants.stream().filter(p -> p.slotIndex() == turn.activeSlot())
            .forEach(p -> remaining.remove(p.sessionId()));
        return new MatchSnapshot(
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
            MatchPhase.COMPLETION,
            loadedSessions,
            applied,
            turn,
            phaseRevision + 1,
            pending.map(MatchCompletion::deadlineNanos).orElse(deadlineNanos),
            pending,
            movements,
            closure,
            remaining
        );
    }

    public int firstTurnSlot() {
        return turn.activeSlot();
    }

    public int initialWindRaw() {
        return turn.windRaw();
    }

    public static MatchSnapshot from(Match match) {
        return new MatchSnapshot(
            match.id(),
            match.roomId(),
            match.roomGeneration(),
            match.channelId(),
            match.ownerSessionId(),
            match.mode(),
            match.rule(),
            match.definitions(),
            match.participants(),
            match.captains(),
            match.phase(),
            match.loadedSessions(),
            match.world(),
            match.turn(),
            match.phaseRevision(),
            match.deadlineNanos(),
            match.completion(),
            match.movements(),
            match.closure(),
            match.attacks()
        );
    }
}
