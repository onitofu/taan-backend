package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchParticipationStateTest {
    @Test
    void measuresOnlyControllableWindowsAndFreezesTheFirstTerminalTime() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var initial = MatchParticipationState.initial();

        assertEquals(0, initial.durationMillis());
        assertSame(initial, initial.finish(1, false));
        var started = initial.start(first, 0);
        assertEquals(0, started.durationMillis());
        var passed = started.finish(1000000000L, true);
        assertTrue(passed.voluntarilyPassed().contains(first));
        var next = passed.start(second, 3000000000L).finish(10000000000L, false);
        var repeated = next.start(first, 11000000000L).finish(13000000000L, false);
        var ended = repeated.stop(14000000000L);

        assertEquals(2000, ended.longestTurnMillis().get(first));
        assertEquals(7000, ended.longestTurnMillis().get(second));
        assertEquals(14000, ended.durationMillis());
        assertSame(ended, ended.stop(99000000000L));
        assertSame(ended, ended.start(first, 99000000000L));
        assertEquals(1800000, initial.start(first, 0).stop(9999999999999L).durationMillis());
        assertEquals(0, initial.start(first, 10).stop(0).durationMillis());
        assertEquals(0, initial.stop(10).durationMillis());
    }
}
