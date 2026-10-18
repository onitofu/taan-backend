package taan.online.room.model;

import java.util.Set;

public record RoomMapCatalog(Set<Integer> mapIds) {
    public RoomMapCatalog {
        mapIds = Set.copyOf(mapIds);

        if (mapIds.isEmpty() || mapIds.stream().anyMatch(id -> id < 1 || id > 65535)) {
            throw new IllegalArgumentException("Invalid room map catalog");
        }
    }

    public boolean contains(int mapId) {
        return mapIds.contains(mapId);
    }
}
