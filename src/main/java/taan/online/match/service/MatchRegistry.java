package taan.online.match.service;

import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.mapId;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomGeneration;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.roomMode;
import static taan.online.logging.LogField.sessionId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.MapSpawnDefinition;
import taan.online.match.model.Match;
import taan.online.match.model.MatchDefinitions;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.RoomMatchLifecycle;
import taan.online.room.service.model.RoomMatchDraft;
import taan.online.room.service.model.RoomMatchParticipant;
import taan.online.room.service.model.RoomMatchReservationOutcome;
import taan.online.room.service.model.RoomMatchReservationResult;
import taan.online.room.service.model.RoomMatchToken;

public final class MatchRegistry implements RoomMatchLifecycle {
    private static final EventLogger LOG = EventLogger.forClass(MatchRegistry.class);

    private final MatchDefinitions definitions;
    private final Map<MatchId, Match> matches = new HashMap<>();

    public MatchRegistry(MatchDefinitions definitions) {
        this.definitions = Objects.requireNonNull(definitions);
    }

    @Override
    public synchronized RoomMatchReservationResult reserve(RoomMatchDraft draft) {
        var token = draft.token();
        var existing = current(token);

        if (existing.isPresent()) {
            return result(RoomMatchReservationOutcome.UNCHANGED, token, "ALREADY_RESERVED");
        }

        String rejection = rejection(draft);

        if (rejection != null) {
            log(MatchLogEvent.RESERVATION_REJECTED, draft, rejection);
            return result(RoomMatchReservationOutcome.REJECTED, null, rejection);
        }

        try {
            var participants = participants(draft);
            var match = new Match(
                new MatchId(token.matchId()),
                token.roomId(),
                token.generation(),
                draft.channelId(),
                draft.ownerSessionId(),
                draft.settings().mode(),
                draft.settings().rule(),
                definitions,
                participants,
                draft.captains()
            );
            matches.put(match.id(), match);
        } catch (IllegalArgumentException failure) {
            log(MatchLogEvent.RESERVATION_REJECTED, draft, "INVALID_SETUP");
            return result(RoomMatchReservationOutcome.REJECTED, null, "INVALID_SETUP");
        } catch (RuntimeException failure) {
            LOG.failure(
                MatchLogEvent.RESERVATION_FAILED,
                failure,
                matchId(token.matchId()),
                roomId(token.roomId().value()),
                roomGeneration(token.generation())
            );
            throw failure;
        }

        log(MatchLogEvent.RESERVED, draft, "RESERVED");
        return result(RoomMatchReservationOutcome.RESERVED, token, "RESERVED");
    }

    @Override
    public synchronized RoomMatchReservationResult commit(RoomMatchToken token) {
        var match = find(token);

        if (match == null) {
            return result(RoomMatchReservationOutcome.STALE, null, "STALE_MATCH");
        }

        if (!match.commit()) {
            return result(RoomMatchReservationOutcome.UNCHANGED, token, "ALREADY_COMMITTED");
        }

        log(MatchLogEvent.LOADING, token, "LOADING");
        return result(RoomMatchReservationOutcome.COMMITTED, token, "LOADING");
    }

    @Override
    public synchronized RoomMatchReservationResult cancel(RoomMatchToken token, String closeReason) {
        var match = find(token);

        if (match == null) {
            return result(RoomMatchReservationOutcome.STALE, null, "STALE_MATCH");
        }

        var previous = match.phase();
        match.cancel();
        matches.remove(match.id());
        log(
            previous == MatchPhase.RESERVED ? MatchLogEvent.RESERVATION_ROLLED_BACK : MatchLogEvent.CANCELLED,
            token,
            closeReason
        );
        return result(RoomMatchReservationOutcome.ROLLED_BACK, token, closeReason);
    }

    public synchronized Optional<MatchSnapshot> current(RoomMatchToken token) {
        return Optional.ofNullable(find(token)).map(MatchSnapshot::from);
    }

    private Match find(RoomMatchToken token) {
        var match = matches.get(new MatchId(token.matchId()));
        return match != null && match.roomId().equals(token.roomId())
            && match.roomGeneration().equals(token.generation())
            ? match : null;
    }

    private String rejection(RoomMatchDraft draft) {
        if (draft.settings().mapId() != definitions.map().mapId()) {
            return "UNSUPPORTED_MAP";
        }

        if (matches.values().stream().anyMatch(
            value -> value.roomId().equals(draft.token().roomId())
                || value.id().value().equals(draft.token().matchId())
        )) {
            return "ALREADY_RESERVED";
        }

        for (var participant : draft.participants()) {
            var membership = participant.membership();

            if (!membership.roomId().equals(draft.token().roomId())
                || !membership.generation().equals(draft.token().generation())
                || membership.channelId() != draft.channelId()) {
                return "ADMISSION_MISMATCH";
            }

            if (participant.slot().participant().weaponId() != 0) {
                return "UNSUPPORTED_WEAPON";
            }

            if (matches.values().stream().flatMap(value -> value.participants().stream()).anyMatch(
                value -> value.sessionId().equals(membership.sessionId())
                    || value.character().accountId().equals(participant.slot().participant().character().accountId())
                    || value.character().id().equals(participant.slot().participant().character().id())
            )) {
                return "PARTICIPANT_IN_MATCH";
            }
        }

        return null;
    }

    private ArrayList<MatchParticipant> participants(RoomMatchDraft draft) {
        var result = new ArrayList<MatchParticipant>();
        var used = new HashSet<Integer>();
        var weapon = definitions.weapons().getFirst();
        var ordered = draft.participants().stream().sorted(Comparator.comparingInt(value -> value.slot().index()))
            .toList();

        for (RoomMatchParticipant participant : ordered) {
            var slot = participant.slot();
            int side = slot.team() == RoomTeam.BLUE ? 1 : 2;
            var spawn = definitions.map().spawns().stream()
                .filter(value -> value.valid() && !used.contains(value.index()))
                .filter(value -> value.side() == side || value.side() == 0)
                .min(
                    Comparator.comparingInt(
                        (MapSpawnDefinition value) -> value.side() == side
                            ? 0 : 1
                    ).thenComparingInt(MapSpawnDefinition::index)
                )
                .orElseThrow(() -> new IllegalStateException("Insufficient authorized map spawns"));
            used.add(spawn.index());
            result.add(
                new MatchParticipant(
                    participant.membership().sessionId(),
                    participant.membership().admissionId(),
                    slot.index(),
                    slot.team(),
                    slot.participant().character(),
                    0,
                    spawn,
                    side - 1,
                    weapon.resourceMaxHp(),
                    weapon.resourceMaxHp(),
                    0,
                    weapon.maxEnergy()
                )
            );
        }

        return result;
    }

    private static RoomMatchReservationResult result(
        RoomMatchReservationOutcome outcome,
        RoomMatchToken token,
        String reason
    ) {
        return new RoomMatchReservationResult(outcome, reason, token);
    }

    private static void log(MatchLogEvent event, RoomMatchToken token, String result) {
        LOG.log(
            event,
            matchId(token.matchId()),
            roomId(token.roomId().value()),
            roomGeneration(token.generation()),
            reason(result)
        );
    }

    private static void log(MatchLogEvent event, RoomMatchDraft draft, String result) {
        LOG.log(
            event,
            matchId(draft.token().matchId()),
            roomId(draft.token().roomId().value()),
            roomGeneration(draft.token().generation()),
            sessionId(draft.ownerSessionId()),
            channelId(draft.channelId()),
            mapId(draft.settings().mapId()),
            roomMode(draft.settings().mode().name()),
            reason(result)
        );
    }
}
