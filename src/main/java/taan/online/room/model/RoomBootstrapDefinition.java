package taan.online.room.model;

public record RoomBootstrapDefinition(int mapId, int weaponId) {
    public RoomBootstrapDefinition {
        if (mapId < 1 || mapId > 65535 || !Room.isSelectableWeapon(weaponId)) {
            throw new IllegalArgumentException("Invalid room bootstrap definition");
        }
    }
}
