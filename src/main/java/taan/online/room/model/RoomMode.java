package taan.online.room.model;

public enum RoomMode {
    ITEM, NO_ITEM, CAPTAIN_ITEM, CAPTAIN_NO_ITEM;

    public boolean usesItems() {
        return this == ITEM || this == CAPTAIN_ITEM;
    }

    public boolean requiresCaptainSelection() {
        return this == CAPTAIN_ITEM || this == CAPTAIN_NO_ITEM;
    }
}
