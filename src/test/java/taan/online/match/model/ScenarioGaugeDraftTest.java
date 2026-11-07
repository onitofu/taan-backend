package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomTeam;

class ScenarioGaugeDraftTest {
    @Test
    void validatesGenerationBeforeReplayingTheUnchangedWireOrderIncludingTiedTicks() {
        var original = gauges(90, 95);
        var draft = new ScenarioGaugeDraft(original, RoomTeam.BLUE, RoomMode.NO_ITEM, 4);
        draft.pickup(100);
        draft.pickup(200);
        draft.claim(100, new TeamGaugeDelta(1, 210, 195));
        draft.claim(100, new TeamGaugeDelta(0, 145, 95));
        draft.claim(200, new TeamGaugeDelta(0, 195, 145));

        var result = draft.finish(true);
        assertEquals(new TeamGaugeState(195, 195), result.get(RoomTeam.BLUE));
        assertEquals(new TeamGaugeState(90, 95), original.get(RoomTeam.BLUE));
        assertEquals(original.get(RoomTeam.RED), result.get(RoomTeam.RED));
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }

    @Test
    void permitsEmptyScenariosAndCappedPickupsButRejectsGaugeInItemModes() {
        for (var mode : RoomMode.values()) {
            var original = gauges(400, 400);
            var draft = new ScenarioGaugeDraft(original, RoomTeam.BLUE, mode, 2);
            draft.pickup(10);
            assertSame(original, draft.finish(false));
            assertThrows(ScenarioWorldException.class, () -> draft.claim(10, new TeamGaugeDelta(0, 450, 400)));
        }

        var draft = fresh();
        assertEquals(gauges(0, 0), draft.finish(false));
        draft.claim(10, new TeamGaugeDelta(1, 20, 0));
        assertEquals(new TeamGaugeState(20, 20), draft.finish(true).get(RoomTeam.BLUE));
    }

    @Test
    void rejectsMissingDuplicateForgedAndUnrelatedClaimsWithoutChangingTheWorld() {
        var missing = fresh();
        missing.pickup(10);
        assertThrows(ScenarioWorldException.class, () -> missing.finish(false));

        var extra = fresh();
        extra.claim(10, new TeamGaugeDelta(0, 50, 0));
        assertThrows(ScenarioWorldException.class, () -> extra.finish(false));

        var chain = fresh();
        chain.pickup(10);
        chain.claim(10, new TeamGaugeDelta(0, 51, 1));
        assertThrows(ScenarioWorldException.class, () -> chain.finish(false));

        var tick = fresh();
        tick.pickup(10);
        tick.claim(11, new TeamGaugeDelta(0, 50, 0));
        assertThrows(ScenarioWorldException.class, () -> tick.finish(false));

        var duplicate = fresh();
        duplicate.claim(10, new TeamGaugeDelta(1, 1, 0));
        duplicate.claim(10, new TeamGaugeDelta(1, 1, 0));
        assertThrows(ScenarioWorldException.class, () -> duplicate.finish(true));

        var miss = fresh();
        miss.claim(10, new TeamGaugeDelta(1, 1, 0));
        assertThrows(ScenarioWorldException.class, () -> miss.finish(false));
        assertThrows(ScenarioWorldException.class, () -> fresh().claim(10, new TeamGaugeDelta(4, 5, 0)));

        for (var delta : new TeamGaugeDelta[]{
            new TeamGaugeDelta(1, 0, 0),
            new TeamGaugeDelta(1, 21, 0),
            new TeamGaugeDelta(1, 1, 1)
        }) {
            var invalid = fresh();
            invalid.claim(10, delta);
            assertThrows(ScenarioWorldException.class, () -> invalid.finish(true));
        }

        var pickup = fresh();
        pickup.pickup(10);
        pickup.claim(10, new TeamGaugeDelta(0, 49, 0));
        assertThrows(ScenarioWorldException.class, () -> pickup.finish(false));
    }

    private ScenarioGaugeDraft fresh() {
        return new ScenarioGaugeDraft(gauges(0, 0), RoomTeam.BLUE, RoomMode.CAPTAIN_NO_ITEM, 2);
    }

    private Map<RoomTeam, TeamGaugeState> gauges(int committed, int pending) {
        return Map.of(RoomTeam.BLUE, new TeamGaugeState(committed, pending), RoomTeam.RED, TeamGaugeState.empty());
    }
}
