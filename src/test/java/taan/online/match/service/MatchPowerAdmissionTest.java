package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.match.model.MatchAttackSelection;
import taan.online.match.service.model.DepartMatchCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.SpentMatchItem;
import taan.online.match.service.model.UseMatchItemCommand;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchPowerAdmissionTest {
    @Test
    void cancelsInvalidIndicesItemsReceiptsRoutesAndPendingSpendWithoutMutatingAnotherInventory() throws Exception {
        for (int index = 0; index < 10; index++) {
            try (var p = new MatchPowerFixture(); var logs = new LogCapture()) {
                if (index != 9) {
                    p.grant(0, index == 4 ? 7 : 5, index != 5);
                }
                p.grant(2, 5, true);
                var before = p.f.runtime.snapshot();
                var base = p.use(index == 0 ? -1 : index == 1 ? 3 : index == 2 ? 255 : 0, index == 3 ? 4 : 5);

                if (index == 8) {
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                }

                var command = new UseMatchItemCommand(
                    base.context(),
                    base.turnRevision(),
                    base.phaseRevision(),
                    base.slot(),
                    base.team(),
                    index == 6 ? 1 : 0,
                    index == 7 ? 1 : 0,
                    base.inventoryIndex(),
                    base.itemId()
                );
                assertFalse(p.energy.activate(command));
                assertSame(before.world(), p.f.runtime.snapshot().world());
                assertSame(before.loot(), p.f.runtime.snapshot().loot());
                assertTrue(p.f.owner.uses.isEmpty());
                assertEquals(RoomMatchEndReason.INVALID_SCENARIO, p.f.callbacks.reason);
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            }
        }

        try (var p = new EnergyRuntimeFixture()) {
            var special = p.use();
            assertFalse(p.activate(new UseMatchItemCommand(
                special.context(),
                special.turnRevision(),
                special.phaseRevision(),
                0,
                1,
                0,
                0,
                0,
                5
            )));
        }
    }

    @Test
    void ignoresForeignContextExpiredTurnAndAdmissionChangesWithoutSpendingTheCard() throws Exception {
        try (var p = new MatchPowerFixture()) {
            p.grant(0, 5, true);
            var before = p.f.runtime.snapshot();
            var use = p.use(0, 5);
            var c = use.context();

            for (var context : List.of(
                new MatchCommandContext(
                    c.matchId(),
                    c.roomId(),
                    c.roomGeneration(),
                    c.channelId() + 1,
                    c.sessionId(),
                    c.admissionId()
                ),
                new MatchCommandContext(
                    c.matchId(),
                    c.roomId(),
                    c.roomGeneration(),
                    c.channelId(),
                    c.sessionId(),
                    UUID.randomUUID()
                ),
                p.context(2)
            )) {
                var foreign = new UseMatchItemCommand(
                    context,
                    use.turnRevision(),
                    use.phaseRevision(),
                    use.slot(),
                    use.team(),
                    0,
                    0,
                    0,
                    5
                );
                assertFalse(p.energy.activate(foreign));
                assertSame(before, p.f.runtime.snapshot());
            }

            var accesses = new AtomicInteger();
            p.f.onAccess = () -> {
                if (accesses.incrementAndGet() == 2) {
                    p.f.admitted = false;
                }
            };
            assertFalse(p.energy.activate(use));
            assertSame(before, p.f.runtime.snapshot());
            assertNull(p.f.callbacks.reason);
        }
    }

    @Test
    void requiresSpendRejectsUnauthorizedVariantsAndKeepsPendingStateOutOfNewMatches() throws Exception {
        for (int invalid = 0; invalid < 4; invalid++) {
            try (var p = new MatchPowerFixture()) {
                p.grant(0, 5, true);
                assertTrue(p.energy.activate(p.use(0, 5)));

                if (invalid == 0) {
                    assertEquals("MISSING_SPEND", p.apply(p.shot()).reason());
                } else if (invalid == 1) {
                    assertFalse(p.energy.record(p.energy.spend(List.of(new SpentMatchItem(6, 0)))));
                } else {
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                    var shot = invalid == 2 ? p.energy.shot(false, 0) : p.shot(false);
                    assertEquals(
                        invalid == 2 ? "UNSUPPORTED_ATTACK_FEATURE" : "INVALID_ATTACK_ANIMATION",
                        p.apply(shot).reason()
                    );
                }

                assertTrue(p.f.match.attacks().isEmpty());
                assertTrue(p.f.match.loot().inventories().isEmpty());
                assertTrue(p.f.owner.spends.isEmpty());
            }
        }

        try (var p = new MatchPowerFixture()) {
            p.grant(0, 5, true);
            assertTrue(p.energy.activate(p.use(0, 5)));
            var departure = new DepartMatchCommand(p.energy.context(), RoomMatchEndReason.DISCONNECTED);
            var result = p.f.runtime.turnWork(() -> p.f.runtime.outcomes.depart(departure));
            p.f.run();
            assertTrue(result.join());
            assertTrue(p.f.match.attacks().isEmpty());
            assertTrue(p.f.match.loot().inventories().isEmpty());
        }

        try (var fresh = new MatchPowerFixture()) {
            assertTrue(fresh.f.runtime.snapshot().attacks().isEmpty());
            assertTrue(fresh.f.runtime.snapshot().loot().inventories().isEmpty());
        }
    }

    @Test
    void boundsUnspentActivationsAndCancelsBothPowerAndSpecialAttemptsBeyondTheLimit() throws Exception {
        for (int item : new int[]{5, 6}) {
            try (var p = new MatchPowerFixture(); var logs = new LogCapture()) {
                for (int index = 0; index < MatchAttackSelection.MAX_ACTIVATIONS; index++) {
                    p.grant(0, 5, true);
                    assertTrue(p.f.match.useItem(p.f.room.owner, 0, 5));
                }

                p.grant(0, 5, true);
                assertFalse(p.energy.activate(p.use(item == 6 ? 255 : 0, item)));
                assertEquals(RoomMatchEndReason.INVALID_SCENARIO, p.f.callbacks.reason);
                assertTrue(logs.text().contains("MATCH_ACTIVE_ITEM_LIMIT"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            }
        }
    }
}
