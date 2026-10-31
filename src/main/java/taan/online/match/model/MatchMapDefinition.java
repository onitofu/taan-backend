package taan.online.match.model;

import java.util.List;
import java.util.Set;

public record MatchMapDefinition(
    int mapId,
    int width,
    int height,
    List<MapSpawnDefinition> spawns,
    Set<Integer> channelKinds,
    List<TerrainLayerDefinition> terrainLayers
) {
    public MatchMapDefinition {
        spawns = List.copyOf(spawns);
        terrainLayers = List.copyOf(terrainLayers);
        channelKinds = Set.copyOf(channelKinds);

        if (channelKinds.isEmpty() || !Set.of(0, 1, 4, 6).containsAll(channelKinds)) {
            throw new IllegalArgumentException("Invalid map channel kinds");
        }

        if (mapId < 1 || mapId > 65535 || width < 1 || height < 1
            || spawns.isEmpty() || terrainLayers.isEmpty()) {
            throw new IllegalArgumentException("Invalid match map definition");
        }

        if (spawns.stream().map(MapSpawnDefinition::index).distinct().count() != spawns.size()) {
            throw new IllegalArgumentException("Duplicate map spawn index");
        }

        if (spawns.stream().anyMatch(
            spawn -> spawn.rawX() >= width || spawn.rawY() >= height
                || spawn.settledX() >= width || spawn.settledY() >= height
        )) {
            throw new IllegalArgumentException("Map spawn is outside map bounds");
        }
    }
}
