package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.MatchRoomFixture;
import taan.online.room.model.RoomMode;

class MatchTurnPolicyTest {
    @Test
    void reproducesIndependentResearchOrderAndTwentyWindCycles() throws Exception {
        try (var fixture = new MatchRoomFixture(4, RoomMode.ITEM)) {
            var snapshot = fixture.scenario.matches.current(fixture.scenario.rooms.reserveMatch(fixture.owner).token())
                .orElseThrow();
            String oracle;

            try (var input = getClass().getResourceAsStream("/match/turn-policy-oracle.csv")) {
                oracle = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }

            var policy = new MatchTurnPolicy();

            for (String line : oracle.lines().skip(1).toList()) {
                var values = line.split(";");
                var turn = policy.initial(snapshot.participants(), Long.parseLong(values[0]), 0);
                assertEquals(integers(values[1]), turn.order());
                assertEquals(Integer.parseInt(values[2]), turn.windRaw());
                assertEquals(1, turn.round());
                assertEquals(0, turn.energyGrant());
                assertEquals(0, turn.pickerBase());
                assertEquals(0, turn.boxAttemptBudget());

                for (int expected : integers(values[3])) {
                    for (int index = 0; index < 3; index++) {
                        int wind = turn.windRaw();
                        turn = policy.next(turn, Set.of(0, 1, 2, 3), 100);
                        assertEquals(wind, turn.windRaw());
                        assertEquals(0, turn.pickerBase());
                        assertEquals(0, turn.boxAttemptBudget());
                    }

                    int previous = turn.windRaw();
                    turn = policy.next(turn, Set.of(0, 1, 2, 3), 100);
                    assertEquals(expected, turn.windRaw());
                    assertTrue(Math.abs(previous - expected) <= 20);
                }

                assertEquals(21, turn.round());
                assertEquals(81, turn.revision());
                assertEquals(Long.parseLong(values[4]), turn.randomState());
            }
        }
    }

    @Test
    void keepsAnUnequalTeamQueueFixedAndSkipsEliminatedMembers() {
        try (var fixture = new MatchRoomFixture(4, RoomMode.ITEM)) {
            var members = fixture.scenario.matches.current(fixture.scenario.rooms.reserveMatch(fixture.owner).token())
                .orElseThrow().participants();
            var policy = new MatchTurnPolicy();

            for (long seed = 0; seed < 30; seed++) {
                var three = policy.initial(members.subList(0, 3), seed, 0);
                assertEquals(Set.of(0, 1, 2), Set.copyOf(three.order()));
                three = policy.next(three, Set.of(2), 1);
                assertEquals(2, three.activeSlot());
                var next = policy.next(three, Set.of(2), 2);
                assertEquals(three.round() + 1, next.round());
                assertThrows(UnsupportedOperationException.class, () -> next.remainingThisRound().clear());
                assertThrows(IllegalStateException.class, () -> policy.next(next, Set.of(), 3));

                var redLarger = policy.initial(members.subList(1, 4), seed, 0);
                assertEquals(Set.of(1, 2, 3), Set.copyOf(redLarger.order()));
            }
        }
    }

    @Test
    void redrawsInsteadOfBiasingThePartialUpperBucket() {
        var draw = new MatchTurnPolicy().bounded(186667457325737L, 101);
        assertTrue(draw.value() >= 0 && draw.value() < 101);
        assertTrue(draw.state() != (2147483647L << 17));
    }

    private List<Integer> integers(String text) {
        return Arrays.stream(text.substring(1, text.length() - 1).split(",")).map(Integer::parseInt).toList();
    }
}
