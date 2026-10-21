package taan.online.match.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import taan.online.character.model.CharacterId;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchOutcomeCause;
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchResult;
import taan.online.room.model.RoomTeam;

final class MatchResultFixtures {
    private MatchResultFixtures() {
    }

    static MatchResult result(long first, long second) {
        return new MatchResult(
            new MatchId(UUID.randomUUID()),
            Instant.parse("2026-10-21T00:00:00Z"),
            Optional.of(RoomTeam.BLUE),
            MatchOutcomeCause.ELIMINATION,
            "damage-medal-v1",
            List.of(
                new MatchParticipantResult(new CharacterId(first), 0, RoomTeam.BLUE, 400, true, true),
                new MatchParticipantResult(new CharacterId(second), 2, RoomTeam.RED, 400, true, true)
            )
        );
    }
}
