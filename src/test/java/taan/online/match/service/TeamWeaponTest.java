package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.TeamScenarioPackets;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.model.RoomMode;

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
            for (int weapon : new int[]{0, 3, 8, 10}) {
                try (var f = new TeamMatchFixture(4, 4, mode)) {
                    f.start(true);

                    for (var items : combinations) {
                        f.weapon(weapon);

                        for (int item : items) {
                            f.activate(item);
                        }

                        var member = f.active();
                        var snapshot = f.snapshot();
                        var target = snapshot.participants().stream().filter(p -> p.team() != member.team())
                            .findFirst().orElseThrow();
                        var packet = TeamScenarioPackets.shot(snapshot, f.selected(), target.slotIndex(), 0);
                        f.spend();
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
        for (var mode : List.of(RoomMode.ITEM, RoomMode.CAPTAIN_ITEM)) {
            for (int weapon : new int[]{0, 3, 8, 10}) {
                try (var f = new TeamMatchFixture(4, 4, mode)) {
                    f.start(true);

                    for (int repeat = 0; repeat < 2; repeat++) {
                        for (int step = 0; step < 100; step++) {
                            f.pass();
                        }

                        f.weapon(weapon);

                        var shooter = f.active();
                        assertEquals(100, f.snapshot().actors().get(shooter.sessionId()).energy());
                        f.activate(6);
                        f.activate(1);
                        f.activate(repeat == 0 ? 1050 : 5);
                        f.activate(7);
                        f.spend();
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
    }
}
