package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import taan.online.match.model.MatchAttackSelection;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.match.service.model.SpentMatchItem;
import taan.online.match.service.model.UseMatchItemCommand;
import taan.online.room.model.RoomMode;

class GhostItemCommandBoundaryTest {
    @Test
    void ignoresUnoccupiedOwnersAndNonActiveSpendingWithoutEndingAnotherPlayersTurn() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            var snapshot = g.snapshot();
            var unknown = new UseMatchItemCommand(
                g.context(0),
                snapshot.turn().revision(),
                snapshot.phaseRevision(),
                7,
                1,
                0,
                0,
                0,
                5
            );
            assertFalse(g.complete(g.f.runtime.turnWork(() -> g.f.runtime.items.activate(unknown))));
            var foreign = new RecordMatchSpendCommand(
                g.context(2),
                snapshot.turn().revision(),
                snapshot.phaseRevision(),
                2,
                2,
                255,
                Collections.emptyList()
            );
            assertFalse(g.complete(g.f.runtime.turnWork(() -> g.f.runtime.items.record(foreign))));
            assertNull(g.f.callbacks.reason);
        }
    }

    @Test
    void rejectsInvalidRoutingFlagsAndExcessiveSpendVectors() throws Exception {
        for (int[] flags : new int[][]{{-1, 0}, {2, 0}, {0, -1}, {0, 2}}) {
            try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
                var snapshot = g.snapshot();
                var command = new UseMatchItemCommand(
                    g.context(0),
                    snapshot.turn().revision(),
                    snapshot.phaseRevision(),
                    0,
                    1,
                    flags[0],
                    flags[1],
                    0,
                    5
                );
                assertFalse(g.complete(g.f.runtime.turnWork(() -> g.f.runtime.items.activate(command))));
            }
        }

        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            var snapshot = g.snapshot();
            var command = new RecordMatchSpendCommand(
                g.context(0),
                snapshot.turn().revision(),
                snapshot.phaseRevision(),
                0,
                1,
                255,
                Collections.nCopies(MatchAttackSelection.MAX_ACTIVATIONS + 1, new SpentMatchItem(5, 0))
            );
            assertFalse(g.complete(g.f.runtime.turnWork(() -> g.f.runtime.items.record(command))));
        }
    }
}
