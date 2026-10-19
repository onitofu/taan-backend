package taan.online.match.service.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import taan.online.match.model.Match;
import taan.online.match.model.MatchActorState;
import taan.online.match.model.MatchDefinitions;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.MatchTurn;
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
    Map<UUID, MatchActorState> actors,
    MatchTurn turn,
    long phaseRevision,
    long deadlineNanos
) {
    public MatchSnapshot {
        participants = List.copyOf(participants);
        loadedSessions = Set.copyOf(loadedSessions);
        actors = Map.copyOf(actors);
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
            match.actors(),
            match.turn(),
            match.phaseRevision(),
            match.deadlineNanos()
        );
    }
}
