package taan.online.room.service.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import taan.online.room.model.CaptainSelectionResult;
import taan.online.room.model.RoomSettings;

public record RoomMatchDraft(
    RoomMatchToken token,
    int channelId,
    UUID ownerSessionId,
    RoomSettings settings,
    List<RoomMatchParticipant> participants,
    CaptainSelectionResult captains,
    int lootPickerScalar
) {
    public RoomMatchDraft {
        Objects.requireNonNull(token);
        Objects.requireNonNull(ownerSessionId);
        Objects.requireNonNull(settings);
        participants = List.copyOf(participants);

        if (lootPickerScalar < 0 || lootPickerScalar > 100) {
            throw new IllegalArgumentException("Invalid channel loot picker scalar");
        }
    }
}
