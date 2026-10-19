package taan.online.match.model;

import java.util.HashMap;
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

    public MatchWorldState placed(UUID session, MatchActorState actor) {
        var updated = new HashMap<>(actors);
        updated.put(session, actor);
        return new MatchWorldState(Math.incrementExact(revision), updated, terrain, falls, processedDeaths, receipt);
    }
}
