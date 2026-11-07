package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.TeamGaugeRules;
import taan.online.match.model.TeamGaugeState;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioIntPoint;
import taan.online.match.protocol.packet.ScenarioMakeItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioTeamGaugeCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.DepartMatchCommand;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.RoomMatchEndReason;

class NoItemScenarioTest {
    @Test
    void collectsBoxesAndCommitsGaugeWithTheWorldOnceInBothModes() throws Exception {
        for (var mode : List.of(RoomMode.NO_ITEM, RoomMode.CAPTAIN_NO_ITEM)) {
            try (var f = new NoItemFixture(mode, RoomRule.BASIC, 2); var logs = new LogCapture()) {
                var before = f.snapshot();
                assertEquals(MatchPhase.ACTION, before.phase());
                assertEquals(1, before.turn().boxAttemptBudget());
                var shot = f.shot(
                    new ScenarioMakeItemBoxCommand(
                        new ScenarioCommandHeader(0, 10, 800, 0),
                        0x0500000000000001L,
                        new ScenarioIntPoint(500, 100)
                    ),
                    new ScenarioEatItemBoxCommand(new ScenarioCommandHeader(0, 11, 850, 0), 0x0500000000000001L, 0, 0),
                    new ScenarioTeamGaugeCommand(new ScenarioCommandHeader(0, 12, 850, 0), 0, 50, 0),
                    new ScenarioTeamGaugeCommand(new ScenarioCommandHeader(0, 12, 1200, 0), 1, 70, 50)
                );

                assertEquals(ScenarioApplicationOutcome.APPLIED, f.apply(shot).outcome());
                var after = f.snapshot();
                assertEquals(new TeamGaugeState(70, 70), after.world().teamGauges().get(RoomTeam.BLUE));
                assertEquals(TeamGaugeState.empty(), before.world().teamGauges().get(RoomTeam.BLUE));
                assertFalse(after.world().boxes().get(0x0500000000000001L).active());
                assertTrue(after.loot().inventories().isEmpty());
                assertEquals(0, after.loot().grantSequence());
                assertEquals(ScenarioApplicationOutcome.DUPLICATE, f.apply(shot).outcome());
                assertEquals(after, f.snapshot());
                assertTrue(logs.text().contains("match.gauge.changed"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));

                f.acknowledge();

                assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                assertEquals(after.world().teamGauges(), f.snapshot().world().teamGauges());
                assertTrue(f.snapshot().loot().offers().isEmpty());
                assertTrue(f.f.owner.bonuses.isEmpty());
            }
        }
    }

    @Test
    void usesEachLevelWithoutFSlotsAndPreservesItsBonusesAcrossShots() throws Exception {
        for (var mode : List.of(RoomMode.NO_ITEM, RoomMode.CAPTAIN_NO_ITEM)) {
            for (int level = 0; level <= 4; level++) {
                try (var f = new NoItemFixture(mode, RoomRule.BASIC, 2)) {
                    f.gauge(RoomTeam.BLUE, level * 100);
                    var shot = f.shot();
                    var expected = mode.requiresCaptainSelection() ? new ArrayList<>(List.of(1))
                        : new ArrayList<Integer>();
                    expected.addAll(TeamGaugeRules.items(level));
                    assertEquals(expected, shot.claims().selectedItems());
                    assertEquals(ScenarioApplicationOutcome.APPLIED, f.apply(shot).outcome());
                    assertEquals(level * 100, f.snapshot().world().teamGauges().get(RoomTeam.BLUE).committed());
                    assertTrue(f.snapshot().loot().inventories().isEmpty());
                    assertTrue(f.snapshot().attacks().isEmpty());
                    f.acknowledge();
                    assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                }
            }
        }
    }

    @Test
    void rejectsAnItemRewardOrForgedGaugeWithoutApplyingHpOrPartialState() throws Exception {
        for (boolean item : new boolean[]{true, false}) {
            try (var f = new NoItemFixture(RoomMode.NO_ITEM, RoomRule.BASIC, 2)) {
                var before = f.snapshot();
                var shot = item ? f.shot(
                    new ScenarioMakeItemBoxCommand(
                        new ScenarioCommandHeader(0, 10, 800, 0),
                        0x0500000000000001L,
                        new ScenarioIntPoint(500, 100)
                    ),
                    new ScenarioEatItemBoxCommand(new ScenarioCommandHeader(0, 11, 850, 0), 0x0500000000000001L, 5, 0)
                ) : f.shot(new ScenarioTeamGaugeCommand(new ScenarioCommandHeader(0, 12, 1200, 0), 1, 21, 0));
                assertEquals(ScenarioApplicationOutcome.REJECTED, f.apply(shot).outcome());
                assertEquals(before.actors(), f.snapshot().actors());
                assertEquals(before.world().teamGauges(), f.snapshot().world().teamGauges());
                assertEquals(before.world().combatStatistics(), f.snapshot().world().combatStatistics());
            }
        }
    }

    @Test
    void keepsTheTeamGaugeAndOriginalRosterAcrossDisconnectAndTurnTimeoutAndResetsTheNextMatch() throws Exception {
        try (var f = new NoItemFixture(RoomMode.NO_ITEM, RoomRule.BASIC, 4)) {
            f.gauge(RoomTeam.BLUE, 210);
            var command = new DepartMatchCommand(f.context(1), RoomMatchEndReason.DISCONNECTED);
            var departure = f.f.runtime.turnWork(() -> f.f.runtime.outcomes.depart(command));
            f.f.run();

            assertTrue(departure.join());
            assertEquals(4, f.snapshot().participants().size());
            assertEquals(210, f.snapshot().world().teamGauges().get(RoomTeam.BLUE).committed());
            assertEquals(1, TeamGaugeRules.level(f.snapshot().participants().size(), 210));

            f.f.timers.advanceSeconds(21);
            f.f.run();
            f.f.timers.advanceSeconds(1);
            f.f.run();

            assertEquals(MatchPhase.ACTION, f.snapshot().phase());
            assertEquals(210, f.snapshot().world().teamGauges().get(RoomTeam.BLUE).committed());
            assertTrue(f.snapshot().turn().revision() > 1);
            assertTrue(f.snapshot().loot().offers().isEmpty());
        }

        try (var f = new NoItemFixture(RoomMode.NO_ITEM, RoomRule.BASIC, 4)) {
            assertTrue(f.snapshot().world().teamGauges().values().stream()
                .allMatch(value -> value.equals(TeamGaugeState.empty())));
        }
    }
}
