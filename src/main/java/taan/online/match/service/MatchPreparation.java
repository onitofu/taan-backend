package taan.online.match.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import taan.online.match.model.MapSpawnDefinition;
import taan.online.match.model.MatchDefinitionCatalog;
import taan.online.match.model.MatchParticipant;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.RoomMatchDraft;
import taan.online.room.service.model.RoomMatchParticipant;

final class MatchPreparation {
    private final MatchDefinitionCatalog definitions;

    MatchPreparation(MatchDefinitionCatalog definitions) {
        this.definitions = definitions;
    }

    String rejection(RoomMatchDraft draft, List<MatchSnapshot> existing) {
        if (!definitions.maps().containsKey(draft.settings().mapId())) {
            return "UNSUPPORTED_MAP";
        }

        if (existing.stream().anyMatch(
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

            if (existing.stream().flatMap(value -> value.participants().stream()).anyMatch(
                value -> value.sessionId().equals(membership.sessionId())
                    || value.character().accountId().equals(participant.slot().participant().character().accountId())
                    || value.character().id().equals(participant.slot().participant().character().id())
            )) {
                return "PARTICIPANT_IN_MATCH";
            }
        }

        return null;
    }

    List<MatchParticipant> participants(RoomMatchDraft draft) {
        var result = new ArrayList<MatchParticipant>();
        var used = new HashSet<Integer>();
        var weapon = definitions.weapons().getFirst();
        var map = definitions.maps().get(draft.settings().mapId());
        var ordered = draft.participants().stream().sorted(Comparator.comparingInt(value -> value.slot().index()))
            .toList();

        for (RoomMatchParticipant participant : ordered) {
            var slot = participant.slot();
            int side = slot.team() == RoomTeam.BLUE ? 1 : 2;
            var spawn = map.spawns().stream()
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
                    weapon.maxEnergy(),
                    slot.state()
                )
            );
        }

        return result;
    }

}
