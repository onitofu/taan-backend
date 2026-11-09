package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.match.model.BattleInventory;
import taan.online.match.model.MatchExitKind;
import taan.online.match.model.MatchItemReceipt;
import taan.online.match.model.MatchItemRoute;
import taan.online.match.model.MatchItemSource;
import taan.online.match.model.MatchLootState;
import taan.online.match.service.model.UseMatchItemCommand;
import taan.online.room.model.RoomMode;

class GhostTeamItemBoundaryTest {
    @Test
    void requiresAConfirmedMatchingReceiptFromTheInventoryOwnersCurrentAdmission() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            g.kill(1, 100, 100);
            var owner = g.context(1);
            var sender = g.context(0).sessionId();

            for (int[] fields : new int[][]{{1, 5, 1, 1, 5}, {0, 7, 1, 1, 5}, {0, 5, 0, 1, 5},
                {0, 5, 1, 0, 5}, {0, 5, 1, 1, 7}}) {
                var receipt = new MatchItemReceipt(
                    1, 1, fields[0], fields[1], fields[2] == 1,
                    fields[3] == 1 ? owner.admissionId() : UUID.randomUUID(),
                    MatchItemSource.GHOST_PICKUP,
                    OptionalLong.empty(),
                    Optional.empty()
                );
                g.f.match.updateLoot(new MatchLootState(
                    Map.of(owner.sessionId(), new BattleInventory(List.of(fields[4], 65535, 65535), List.of(receipt))),
                    Map.of(),
                    Map.of(),
                    1
                ));
                assertFalse(g.f.match.useTeamItem(sender, owner.sessionId(), MatchItemRoute.TEAM, 0, 5));
            }

            assertFalse(g.f.match.useTeamItem(sender, owner.sessionId(), MatchItemRoute.GHOST_TEAM, 0, 5));
            g.f.match.withdraw(owner.sessionId(), MatchExitKind.DEPARTED);
            assertFalse(g.f.match.useTeamItem(sender, owner.sessionId(), MatchItemRoute.TEAM, 0, 5));
        }
    }

    @Test
    void doesNotLetInactiveOrDeadAlliesSpendSomeoneElsesInventory() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            g.kill(0, 100, 100);
            assertFalse(g.f.match.useTeamItem(
                g.context(1).sessionId(),
                g.context(0).sessionId(),
                MatchItemRoute.TEAM,
                0,
                5
            ));
            g.kill(1, 100, 100);
            assertFalse(g.f.match.useTeamItem(
                g.context(0).sessionId(),
                g.context(1).sessionId(),
                MatchItemRoute.TEAM,
                0,
                5
            ));
            assertFalse(g.f.match.makeGhost(UUID.randomUUID(), 0));
            g.f.match.withdraw(g.context(0).sessionId(), MatchExitKind.SURRENDERED);
            assertFalse(g.f.match.makeGhost(g.context(1).sessionId(), 1));
        }
    }

    @Test
    void doesNotConfuseSharedReceiptSourcesRoutesIndexesOrItemIdsWithDuplicates() throws Exception {
        for (int[] change : new int[][]{{3, 1, 0, 5}, {1, 0, 0, 5}, {1, 1, 1, 5}, {1, 1, 0, 7}}) {
            try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
                g.kill(1, 100, 100);
                var owner = g.context(1);
                g.f.match.updateLoot(g.f.match.loot().inserted(
                    owner.sessionId(),
                    owner.admissionId(),
                    1,
                    5,
                    MatchItemSource.GHOST_PICKUP,
                    OptionalLong.empty(),
                    Optional.empty()
                ).confirmed(owner.sessionId(), owner.admissionId(), 0, 5));
                assertTrue(g.f.match.useTeamItem(
                    g.context(0).sessionId(),
                    owner.sessionId(),
                    MatchItemRoute.TEAM,
                    0,
                    5
                ));
                g.f.runtime.refresh();
                var snapshot = g.snapshot();
                var use = new UseMatchItemCommand(
                    g.context(0),
                    snapshot.turn().revision(),
                    snapshot.phaseRevision(),
                    change[0],
                    change[0] == 3 ? 2 : 1, change[1], change[1] == 0 ? 1 : 0, change[2], change[3]
                );
                assertFalse(g.complete(g.f.runtime.turnWork(() -> g.f.runtime.items.activate(use))));
            }
        }
    }
}
