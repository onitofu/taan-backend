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

public record MatchWorldState(
    long revision,
    Map<UUID, MatchActorState> actors,
    List<TerrainLayerDefinition> terrain,
    Map<UUID, List<MatchFall>> falls,
    Set<UUID> processedDeaths,
    Optional<ScenarioCommitReceipt> receipt
) {
    public MatchWorldState {
        actors = Map.copyOf(actors);
        terrain = List.copyOf(terrain);
        falls = falls.entrySet().stream().collect(
            Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))
        );
        processedDeaths = Set.copyOf(processedDeaths);
        Objects.requireNonNull(receipt);
    }

    public static MatchWorldState initial(Map<UUID, MatchActorState> actors, List<TerrainLayerDefinition> terrain) {
        return new MatchWorldState(0, actors, terrain, Map.of(), Set.of(), Optional.empty());
    }

    public static MatchWorldState initial(List<MatchParticipant> roster, List<TerrainLayerDefinition> terrain) {
        var actors = new HashMap<UUID, MatchActorState>();
        roster.forEach(p -> actors.put(
            p.sessionId(),
            new MatchActorState(
                p.spawn().rawX(),
                p.spawn().rawY(),
                p.facing(),
                p.hp(),
                p.energy()
            )
        ));
        return initial(actors, terrain);
    }

    public MatchWorldState energy(UUID session, int value) {
        var actor = actors.get(session);
        return actor.energy() == value ? this
            : placed(session, new MatchActorState(actor.x(), actor.y(), actor.facing(), actor.hp(), value));
    }

    public MatchWorldState placed(UUID session, MatchActorState actor) {
        var updated = new HashMap<>(actors);
        updated.put(session, actor);
        var deaths = new HashSet<>(processedDeaths);

        if (actor.dead()) {
            deaths.add(session);
        }

        return new MatchWorldState(Math.incrementExact(revision), updated, terrain, falls, deaths, receipt);
    }
}
