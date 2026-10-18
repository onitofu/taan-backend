package taan.online.room.service;

import java.util.List;
import java.util.concurrent.CompletionStage;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterId;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomTeamChange;
import taan.online.room.service.model.RoomSnapshot;

public interface RoomPresenceOutput {
    CompletionStage<Void> initialize(RoomSnapshot snapshot);

    CompletionStage<Void> update(RoomSnapshot snapshot, List<Integer> changedSlots, boolean ownerChanged);

    CompletionStage<Void> slotAvailability(int slotIndex, RoomSlotState state);

    CompletionStage<Void> teamChanged(RoomTeamChange change);

    CompletionStage<Void> readyChanged(AccountId accountId, boolean ready);

    CompletionStage<Void> weaponChanged(CharacterId characterId, int weaponId);

    CompletionStage<Void> settingsChanged(RoomSettings settings, boolean rejected);

    CompletionStage<Void> expelled(AccountId targetAccountId);

    CompletionStage<Void> captainSelectionOpened();

    CompletionStage<Void> captainCandidateChanged(long characterId, boolean candidate);

    CompletionStage<Void> notice(String message);

    void close(String reason);
}
