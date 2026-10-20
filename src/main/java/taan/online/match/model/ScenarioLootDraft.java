package taan.online.match.model;

import java.util.Optional;
import java.util.OptionalLong;

public final class ScenarioLootDraft {
    private MatchLootState loot;

    public ScenarioLootDraft(MatchLootState original) {
        loot = original;
    }

    public void pickup(MatchParticipant recipient, long turnRevision, String scenarioHash, long boxId, int itemId) {
        loot = loot.inserted(
            recipient.sessionId(),
            recipient.admissionId(),
            turnRevision,
            itemId,
            MatchItemSource.PROJECTILE_PICKUP,
            OptionalLong.of(boxId),
            Optional.of(scenarioHash)
        );
    }

    public MatchLootState finish(MatchWorldState world) {
        for (var entry : world.actors().entrySet()) {
            if (entry.getValue().dead()) {
                loot = loot.without(entry.getKey());
            }
        }

        return loot;
    }
}
