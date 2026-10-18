package taan.online.match.service.model;

import java.util.List;
import java.util.Map;
import taan.online.match.model.MapSpawnDefinition;
import java.util.UUID;
import taan.online.match.model.Match;
import taan.online.match.model.MatchDefinitions;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
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
    int firstTurnSlot,
    int initialWindRaw,
    Map<UUID, MapSpawnDefinition> placements,
    long phaseRevision,
    long deadlineNanos
) {
    public MatchSnapshot {
        participants = List.copyOf(participants);
        placements = Map.copyOf(placements);
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
            match.firstTurnSlot(),
            match.initialWindRaw(),
            match.placements(),
            match.phaseRevision(),
            match.deadlineNanos()
        );
    }
}
