package taan.online.match.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public record MatchLootState(
    Map<UUID, BattleInventory> inventories,
    Map<UUID, MatchBonusOffer> offers,
    Map<Integer, PowerQuizOpportunity> quizOpportunities,
    long grantSequence
) {
    public MatchLootState {
        inventories = Map.copyOf(inventories);
        offers = Map.copyOf(offers);
        quizOpportunities = Map.copyOf(quizOpportunities);
    }

    public static MatchLootState empty() {
        return new MatchLootState(Map.of(), Map.of(), Map.of(), 0);
    }

    public BattleInventory inventory(UUID session) {
        return inventories.getOrDefault(session, BattleInventory.empty());
    }

    public MatchLootState offered(MatchBonusOffer offer) {
        var next = new HashMap<>(offers);
        next.put(offer.sessionId(), offer);
        return new MatchLootState(inventories, next, quizOpportunities, grantSequence);
    }

    public MatchLootState expired(UUID session) {
        if (!offers.containsKey(session)) {
            return this;
        }

        var next = new HashMap<>(offers);
        next.remove(session);
        return new MatchLootState(inventories, next, quizOpportunities, grantSequence);
    }

    public MatchLootState granted(UUID session, int itemId) {
        var offer = offers.get(session);

        if (offer == null || offer.consumed()) {
            return this;
        }

        var nextOffers = new HashMap<>(offers);
        nextOffers.put(session, offer.used());
        return new MatchLootState(inventories, nextOffers, quizOpportunities, grantSequence).inserted(
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
        return new MatchLootState(nextInventories, offers, quizOpportunities, sequence);
    }

    public MatchLootState openQuiz(
        List<MatchParticipant> participants,
        MatchWorldState before,
        MatchResolutionState resolution,
        MatchTurn turn,
        MatchWorldState prepared
    ) {
        var receipt = prepared.receipt().orElseThrow();
        var shooter = participants.stream().filter(member -> member.sessionId().equals(receipt.sessionId()))
            .findFirst().orElseThrow();
        var next = new HashMap<Integer, PowerQuizOpportunity>();

        for (var member : participants) {
            if (member.team() != shooter.team() && !resolution.exits().containsKey(member.sessionId())
                && !before.actors().get(member.sessionId()).dead()) {
                var opportunity = new PowerQuizOpportunity(
                    member.sessionId(),
                    member.admissionId(),
                    member.slotIndex(),
                    shooter.slotIndex(),
                    turn.revision(),
                    receipt.fingerprint(),
                    Math.clamp(turn.pickerBase(), 0, 100),
                    false
                );
                next.put(member.slotIndex(), opportunity);
            }
        }

        return new MatchLootState(inventories, offers, next, grantSequence);
    }

    public MatchLootState grantQuiz(int slot, int itemId) {
        var opportunity = quizOpportunities.get(slot);

        if (opportunity == null || opportunity.consumed()) {
            return this;
        }

        var next = new HashMap<>(quizOpportunities);
        next.put(slot, opportunity.used());
        return new MatchLootState(inventories, offers, next, grantSequence).inserted(
            opportunity.sessionId(),
            opportunity.admissionId(),
            opportunity.turnRevision(),
            itemId,
            MatchItemSource.POWER_QUIZ,
            OptionalLong.empty(),
            Optional.of(opportunity.scenarioHash())
        );
    }

    public MatchLootState closeQuiz(MatchWorldState world) {
        if (quizOpportunities.isEmpty()) {
            return this;
        }

        var next = new HashMap<>(inventories);
        next.keySet().removeIf(session -> world.actors().get(session).dead());
        return new MatchLootState(next, offers, Map.of(), grantSequence);
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
        return new MatchLootState(next, offers, quizOpportunities, grantSequence);
    }

    public MatchLootState without(UUID session) {
        if (!inventories.containsKey(session) && !offers.containsKey(session)
            && quizOpportunities.values().stream().noneMatch(value -> value.sessionId().equals(session))) {
            return this;
        }

        var nextInventories = new HashMap<>(inventories);
        var nextOffers = new HashMap<>(offers);
        nextInventories.remove(session);
        nextOffers.remove(session);
        var nextQuiz = new HashMap<>(quizOpportunities);
        nextQuiz.values().removeIf(value -> value.sessionId().equals(session));
        return new MatchLootState(nextInventories, nextOffers, nextQuiz, grantSequence);
    }
}
