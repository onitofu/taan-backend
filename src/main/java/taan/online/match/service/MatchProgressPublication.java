package taan.online.match.service;

import taan.online.match.model.MatchResult;
import taan.online.room.service.model.RoomMatchCharacterProgress;

final class MatchProgressPublication {
    private MatchProgressPublication() {
    }

    static void publish(MatchRuntime runtime, MatchResult result) {
        if (runtime.callbacks == null) {
            return;
        }

        var updates = runtime.snapshot().participants().stream().flatMap(member -> result.participants().stream()
            .filter(p -> p.characterId().equals(member.character().id()) && p.experienceReward() != null)
            .map(p -> new RoomMatchCharacterProgress(
                member.sessionId(),
                member.admissionId(),
                p.experienceReward().after(),
                p.goldReward() == null ? null : p.goldReward().after()
            ))).toList();
        runtime.callbacks.progressCommitted(runtime.token(), updates);
    }
}
