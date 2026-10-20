package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.TeamScenarioPackets;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.UseMatchItemCommand;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomTeam;

class TeamWeaponTest {
    @Test
    void appliesAllPersonalCardsAndTheirCombinationsForEveryWeaponWithAndWithoutCaptainRepeats() throws Exception {
        var combinations = new ArrayList<List<Integer>>();
        combinations.add(List.of());

        for (int item : new int[]{1, 2, 3, 5, 7, 1005, 1010, 1015, 1020, 1025, 1030, 1035, 1040, 1045, 1050}) {
            combinations.add(List.of(item));
        }

        combinations.addAll(List.of(List.of(1, 1, 1), List.of(2, 3, 7), List.of(1, 5, 7), List.of(1050, 1050, 7)));

        for (var mode : List.of(RoomMode.ITEM, RoomMode.CAPTAIN_ITEM)) {
            for (int weapon : new int[]{0, 8, 10}) {
                try (var f = new TeamMatchFixture(4, 4, mode)) {
                    f.start(true);

                    for (var items : combinations) {
                        f.weapon(weapon);

                        for (int item : items) {
                            activate(f, item);
                        }

                        var member = f.active();
                        var snapshot = f.snapshot();
                        var target = snapshot.participants().stream().filter(p -> p.team() != member.team())
                            .findFirst().orElseThrow();
                        var packet = TeamScenarioPackets.shot(snapshot, f.selected(), target.slotIndex(), 0);
                        spend(f);
                        var result = f.apply(packet);
                        assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome(), result.reason());
                        assertTrue(result.committed().orElseThrow().scenario().journal().missileDefinitions().values()
                            .stream().allMatch(key -> key.weaponId() == weapon));
                        assertEquals(snapshot.actors(), f.snapshot().actors());
                        f.acknowledge();
                        assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                    }
                }
            }
        }
    }

    @Test
    void rechargesAndReusesEveryWeaponsSpecialWithPowerRepeatDamageBoostAndGuidance() throws Exception {
        for (int weapon : new int[]{0, 8, 10}) {
            try (var f = new TeamMatchFixture(4, 4, RoomMode.CAPTAIN_ITEM)) {
                f.start(true);

                for (int repeat = 0; repeat < 2; repeat++) {
                    for (int step = 0; step < 100; step++) {
                        f.pass();
                    }

                    f.weapon(weapon);

                    var shooter = f.active();
                    assertEquals(100, f.snapshot().actors().get(shooter.sessionId()).energy());
                    activate(f, 6);
                    activate(f, 1);
                    activate(f, repeat == 0 ? 1050 : 5);
                    activate(f, 7);
                    spend(f);
                    assertEquals(0, f.snapshot().actors().get(shooter.sessionId()).energy());
                    int target = f.snapshot().participants().stream().filter(p -> p.team() != shooter.team())
                        .findFirst().orElseThrow().slotIndex();
                    var result = f.shot(target, 0);
                    assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome(), result.reason());
                    assertEquals(0, f.snapshot().actors().get(shooter.sessionId()).energy());
                    f.acknowledge();
                    assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                }
            }
        }
    }

    private void activate(TeamMatchFixture f, int item) throws Exception {
        var member = f.active();
        var context = f.context(member.sessionId());
        int index = 255;

        if (item != 6) {
            index = f.snapshot().loot().inventory(member.sessionId()).firstEmpty();
            MatchInventoryTestAccess.grantControlled(f.scenario.matches, f.token, member.slotIndex(), item);
            assertTrue(f.scenario.matches.confirmItemReceipt(new ConfirmMatchItemReceiptCommand(
                context,
                member.slotIndex(),
                index,
                item
            )).toCompletableFuture().join());
        }

        var snapshot = f.snapshot();
        assertTrue(f.scenario.matches.useItem(new UseMatchItemCommand(
            context,
            snapshot.turn().revision(),
            snapshot.phaseRevision(),
            member.slotIndex(),
            member.team() == RoomTeam.BLUE ? 1 : 2,
            0,
            0,
            index,
            item
        )).toCompletableFuture().join());
    }

    private void spend(TeamMatchFixture f) {
        var member = f.active();
        var snapshot = f.snapshot();
        assertTrue(f.scenario.matches.recordSpend(new RecordMatchSpendCommand(
            f.context(member.sessionId()),
            snapshot.turn().revision(),
            snapshot.phaseRevision(),
            member.slotIndex(),
            member.team() == RoomTeam.BLUE ? 1 : 2,
            255,
            List.of()
        )).toCompletableFuture().join());
    }
}
