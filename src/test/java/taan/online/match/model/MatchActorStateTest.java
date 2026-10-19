package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MatchActorStateTest {
    @Test
    void representsDeathWithoutChangingTheStartingParticipant() {
        assertTrue(new MatchActorState(10, 20, 0, 0, 0).dead());
        assertFalse(new MatchActorState(10, 20, 1, 1, 100).dead());
    }

    @Test
    void rejectsInvalidFacingAndNegativeResources() {
        assertThrows(IllegalArgumentException.class, () -> new MatchActorState(0, 0, -1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchActorState(0, 0, 2, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchActorState(0, 0, 1, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchActorState(0, 0, 1, 1, -1));
    }
}
