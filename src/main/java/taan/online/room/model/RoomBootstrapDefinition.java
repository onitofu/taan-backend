package taan.online.room.model;

public record RoomBootstrapDefinition(int mapId, int weaponId) {
    public RoomBootstrapDefinition {
        if (mapId < 1 || mapId > 65535 || weaponId < 0 || weaponId > 65535) {
            throw new IllegalArgumentException("Invalid room bootstrap definition");
        }
    }
}
