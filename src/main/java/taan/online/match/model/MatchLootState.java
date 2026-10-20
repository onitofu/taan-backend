package taan.online.match.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public record MatchLootState(
    Map<UUID, BattleInventory> inventories,
    Map<UUID, MatchBonusOffer> offers,
    long grantSequence
) {
    public MatchLootState {
        inventories = Map.copyOf(inventories);
        offers = Map.copyOf(offers);
    }

    public static MatchLootState empty() {
        return new MatchLootState(Map.of(), Map.of(), 0);
    }

    public BattleInventory inventory(UUID session) {
        return inventories.getOrDefault(session, BattleInventory.empty());
    }

    public MatchLootState offered(MatchBonusOffer offer) {
        var next = new HashMap<>(offers);
        next.put(offer.sessionId(), offer);
        return new MatchLootState(inventories, next, grantSequence);
    }

    public MatchLootState expired(UUID session) {
        var next = new HashMap<>(offers);
        next.remove(session);
        return new MatchLootState(inventories, next, grantSequence);
    }

    public MatchLootState granted(UUID session, int itemId) {
        var offer = offers.get(session);

        if (offer == null || offer.consumed()) {
            return this;
        }

        var nextOffers = new HashMap<>(offers);
        nextOffers.put(session, offer.used());
        return new MatchLootState(inventories, nextOffers, grantSequence).inserted(
            session,
            offer.admissionId(),
            offer.turnRevision(),
            itemId,
            MatchItemSource.END_TURN_BONUS,
            OptionalLong.empty(),
            Optional.empty()
        );
    }

    public MatchLootState inserted(
        UUID session,
        UUID admission,
        long turn,
        int itemId,
        MatchItemSource source,
        OptionalLong boxId,
        Optional<String> scenarioHash
    ) {
        var inventory = inventory(session);
        int index = inventory.firstEmpty();

        if (index < 0) {
            return this;
        }

        long sequence = Math.incrementExact(grantSequence);
        var receipt = new MatchItemReceipt(
            sequence,
            turn,
            index,
            itemId,
            false,
            admission,
            source,
            boxId,
            scenarioHash
        );
        var nextInventories = new HashMap<>(inventories);
        nextInventories.put(session, inventory.inserted(receipt));
        return new MatchLootState(nextInventories, offers, sequence);
    }

    public MatchLootState confirmed(UUID session, UUID admission, int index, int itemId) {
        return withInventory(session, inventory(session).confirmed(admission, index, itemId));
    }

    public MatchLootState discarded(UUID session, int index, int itemId) {
        return withInventory(session, inventory(session).discarded(index, itemId));
    }

    private MatchLootState withInventory(UUID session, BattleInventory inventory) {
        if (inventory.equals(inventory(session))) {
            return this;
        }

        var next = new HashMap<>(inventories);
        next.put(session, inventory);
        return new MatchLootState(next, offers, grantSequence);
    }

    public MatchLootState without(UUID session) {
        var nextInventories = new HashMap<>(inventories);
        var nextOffers = new HashMap<>(offers);
        nextInventories.remove(session);
        nextOffers.remove(session);
        return new MatchLootState(nextInventories, nextOffers, grantSequence);
    }
}
