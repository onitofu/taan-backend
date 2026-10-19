package taan.online.room.model;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record RoomMapCatalog(Set<Integer> mapIds, Map<Integer, Set<Integer>> byKind) {
    public RoomMapCatalog {
        mapIds = Set.copyOf(mapIds);
        byKind = byKind.entrySet().stream().collect(
            Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue()))
        );

        var allMaps = mapIds;

        if (mapIds.isEmpty() || mapIds.stream().anyMatch(id -> id < 1 || id > 65535)
            || !byKind.keySet().equals(Set.of(0, 1, 4, 6))
            || byKind.values().stream().anyMatch(ids -> ids.isEmpty() || !allMaps.containsAll(ids))) {
            throw new IllegalArgumentException("Invalid room map catalog");
        }
    }

    public RoomMapCatalog(Set<Integer> mapIds) {
        this(mapIds, Map.of(0, mapIds, 1, mapIds, 4, mapIds, 6, mapIds));
    }

    public boolean contains(int mapId) {
        return mapIds.contains(mapId);
    }

    public RoomMapCatalog forKind(int kind) {
        return new RoomMapCatalog(byKind.get(kind));
    }

    public int initialMap(int kind, int preferred) {
        var allowed = byKind.get(kind);
        return allowed.contains(preferred) ? preferred
            : allowed.stream().mapToInt(Integer::intValue).min().orElseThrow();
    }
}
