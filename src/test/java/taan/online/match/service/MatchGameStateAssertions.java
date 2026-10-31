package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import taan.online.match.model.MatchLootState;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.MatchSnapshot;

final class MatchGameStateAssertions {
    private MatchGameStateAssertions() {
    }

    static void closedWithoutApplyingGameplay(MatchSnapshot before, MatchSnapshot after) {
        assertEquals(MatchPhase.CLOSED, after.phase());
        assertEquals(before.id(), after.id());
        assertEquals(before.roomGeneration(), after.roomGeneration());
        assertEquals(before.participants(), after.participants());
        assertEquals(before.turn(), after.turn());
        assertEquals(before.world().withoutBoxes(), after.world());
        assertEquals(MatchLootState.empty(), after.loot());
        assertTrue(after.attacks().isEmpty());
        assertTrue(after.phaseRevision() > before.phaseRevision());
    }
}
