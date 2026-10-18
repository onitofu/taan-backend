package taan.online;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterId;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomTeamChange;
import taan.online.room.service.RoomPresenceOutput;
import taan.online.room.service.model.RoomSnapshot;

public final class RecordingRoomOutput implements RoomPresenceOutput {
    public final List<RoomSnapshot> initializations = new ArrayList<>();
    public final List<RoomSnapshot> updates = new ArrayList<>();
    public final List<List<Integer>> changedSlots = new ArrayList<>();
    public final List<Boolean> ownerChanges = new ArrayList<>();
    public final List<Integer> availabilitySlots = new ArrayList<>();
    public final List<RoomSlotState> availabilityStates = new ArrayList<>();
    public final List<RoomTeamChange> teamChanges = new ArrayList<>();
    public final List<AccountId> readyAccounts = new ArrayList<>();
    public final List<Boolean> readyValues = new ArrayList<>();
    public final List<CharacterId> weaponCharacters = new ArrayList<>();
    public final List<Integer> weaponIds = new ArrayList<>();
    public Runnable onWeaponChanged = () -> { };
    public Runnable onReady = () -> { };
    public Runnable onTeamChanged = () -> { };
    public Runnable onSlotAvailability = () -> { };
    public Runnable onInitialize = () -> { };
    public Runnable onUpdate = () -> { };
    public String closed;

    @Override
    public CompletionStage<Void> initialize(RoomSnapshot snapshot) {
        initializations.add(snapshot);
        onInitialize.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> update(RoomSnapshot snapshot, List<Integer> indices, boolean ownerChanged) {
        updates.add(snapshot);
        changedSlots.add(indices);
        ownerChanges.add(ownerChanged);
        onUpdate.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> slotAvailability(int slotIndex, RoomSlotState state) {
        availabilitySlots.add(slotIndex);
        availabilityStates.add(state);
        onSlotAvailability.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> teamChanged(RoomTeamChange change) {
        teamChanges.add(change);
        onTeamChanged.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> readyChanged(AccountId accountId, boolean ready) {
        readyAccounts.add(accountId);
        readyValues.add(ready);
        onReady.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> weaponChanged(CharacterId characterId, int weaponId) {
        weaponCharacters.add(characterId);
        weaponIds.add(weaponId);
        onWeaponChanged.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close(String reason) {
        closed = reason;
    }
}
