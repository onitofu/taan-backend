package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import taan.online.TeamScenarioPackets;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchAttackSelection;
import taan.online.match.model.MatchItemRoute;
import taan.online.match.model.MatchItemSelectionRules;
import taan.online.match.model.MatchItemSource;
import taan.online.match.model.MatchTeamItemState;
import taan.online.match.model.ScenarioWorldException;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.SpentMatchItem;
import taan.online.match.service.model.UseMatchItemCommand;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomTeam;

class MatchGhostTeamItemsTest {
    @Test
    void activatesDeadAlliesCardsOnceAndSpendsSharedSelectionWithTheScenario() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            g.kill(1, 100, 100);
            g.make(1);
            var owner = g.context(1);
            var granted = g.f.match.loot().inserted(
                owner.sessionId(),
                owner.admissionId(),
                0,
                1,
                MatchItemSource.GHOST_PICKUP,
                OptionalLong.empty(),
                Optional.empty()
            );
            g.f.match.updateLoot(granted);
            g.f.runtime.refresh();
            assertTrue(g.complete(MatchItemExecution.confirmItemReceipt(
                g.f.runtime,
                new ConfirmMatchItemReceiptCommand(owner, 1, 0, 1)
            )));
            var before = g.snapshot();
            var use = new UseMatchItemCommand(g.context(0), before.turn().revision(), before.phaseRevision(), 1, 1,
                1,
                0,
                0,
                1);
            assertTrue(g.complete(g.f.runtime.turnWork(() -> g.f.runtime.items.activate(use))));
            assertTrue(g.complete(g.f.runtime.turnWork(() -> g.f.runtime.items.activate(use))));
            assertEquals(List.of(1), g.snapshot().teamItems().get(RoomTeam.BLUE).team());
            assertEquals(1, g.f.owner.uses.size());
            assertEquals(65535, g.snapshot().loot().inventory(owner.sessionId()).slots().getFirst());
            var snapshot = g.snapshot();
            var spend = new RecordMatchSpendCommand(
                g.context(0),
                snapshot.turn().revision(),
                snapshot.phaseRevision(),
                0,
                1,
                255,
                List.of(new SpentMatchItem(1, 1))
            );
            assertTrue(g.complete(g.f.runtime.turnWork(() -> g.f.runtime.items.record(spend))));
            var scene = TeamScenarioPackets.shot(snapshot, List.of(1), 2, 0);
            var command = new ApplyScenarioCommand(
                g.context(0),
                snapshot.turn().revision(),
                snapshot.world().revision(),
                new ScenarioClaimsMapper().map(scene),
                new ScenarioCodec().encode(scene),
                snapshot.phaseRevision()
            );
            assertEquals(ScenarioApplicationOutcome.APPLIED, g.scenario.apply(command).outcome());
            assertTrue(g.snapshot().teamItems().get(RoomTeam.BLUE).team().isEmpty());
            assertTrue(g.snapshot().teamItems().get(RoomTeam.BLUE).receipts().isEmpty());
            assertEquals(1, g.f.owner.spends.size());
            assertTrue(g.f.owner.closed.isEmpty());
        }
    }

    @Test
    void onlyCaptainCanUseLivingAlliesInventoryAndOnlyThroughGhostTeam() throws Exception {
        try (var g = new GhostMatchFixture(RoomMode.CAPTAIN_ITEM)) {
            var owner = g.context(1);
            var inventory = g.f.match.loot().inserted(
                owner.sessionId(),
                owner.admissionId(),
                0,
                5,
                MatchItemSource.END_TURN_BONUS,
                OptionalLong.empty(),
                Optional.empty()
            ).confirmed(owner.sessionId(), owner.admissionId(), 0, 5);
            g.f.match.updateLoot(inventory);
            assertFalse(g.f.match.useTeamItem(g.context(0).sessionId(), owner.sessionId(), MatchItemRoute.TEAM, 0, 5));
            assertFalse(g.f.match.useTeamItem(owner.sessionId(), owner.sessionId(), MatchItemRoute.GHOST_TEAM, 0, 5));
            assertFalse(g.f.match.useTeamItem(
                g.context(2).sessionId(),
                owner.sessionId(),
                MatchItemRoute.GHOST_TEAM,
                0,
                5
            ));
            assertFalse(g.f.match.useTeamItem(
                g.context(0).sessionId(),
                owner.sessionId(),
                MatchItemRoute.GHOST_TEAM,
                -1,
                5
            ));
            assertFalse(g.f.match.useTeamItem(
                g.context(0).sessionId(),
                owner.sessionId(),
                MatchItemRoute.GHOST_TEAM,
                3,
                5
            ));
            assertFalse(g.f.match.useTeamItem(
                g.context(0).sessionId(),
                owner.sessionId(),
                MatchItemRoute.GHOST_TEAM,
                0,
                6
            ));
            assertFalse(g.f.match.useTeamItem(
                g.context(0).sessionId(),
                owner.sessionId(),
                MatchItemRoute.GHOST_TEAM,
                0,
                7
            ));
            g.f.runtime.refresh();
            var snapshot = g.snapshot();
            var use = new UseMatchItemCommand(
                g.context(0),
                snapshot.turn().revision(),
                snapshot.phaseRevision(),
                1,
                1,
                0,
                1,
                0,
                5
            );
            assertTrue(g.complete(g.f.runtime.turnWork(() -> g.f.runtime.items.activate(use))));
            g.f.runtime.refresh();
            assertEquals(List.of(5), g.snapshot().teamItems().get(RoomTeam.BLUE).ghostTeam());
            assertEquals(List.of(1, 5), MatchItemSelectionRules.selectedSources(
                List.of(),
                List.of(),
                List.of(5),
                true,
                List.of()
            ));
            assertEquals(List.of(), MatchItemSelectionRules.selectedSources(
                List.of(),
                List.of(),
                List.of(5),
                false,
                List.of()
            ));
            assertThrows(ScenarioWorldException.class, () -> g.f.runtime.items.prepare());
            g.nextTurn();
            assertTrue(g.f.runtime.items.prepare().isEmpty());
            g.f.match.commitTeamSpend(255, 1);
            assertTrue(g.f.match.teamItems().get(RoomTeam.BLUE).ghostTeam().isEmpty());
        }

        try (var g = new GhostMatchFixture(RoomMode.ITEM)) {
            assertFalse(g.f.match.useTeamItem(
                g.context(0).sessionId(),
                g.context(1).sessionId(),
                MatchItemRoute.GHOST_TEAM,
                0,
                5
            ));
            assertEquals(MatchAttackSelection.NORMAL, g.snapshot().attacks().getOrDefault(
                g.context(0).sessionId(),
                MatchAttackSelection.NORMAL
            ));
            assertEquals(MatchTeamItemState.empty(), new MatchTeamItemState(List.of(), List.of()));
        }
    }
}
