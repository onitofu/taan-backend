package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.MatchBootstrapFixture;
import taan.online.room.model.RoomMode;

class MatchCombatStatisticsTest {
    @Test
    void keepsImmutableTotalsAndRejectsOverflowInsteadOfWrapping() {
        var session = UUID.randomUUID();
        var initial = MatchCombatStatistics.empty();
        var data = new HashMap<>(Map.of(session, 17L));
        var statistics = new MatchCombatStatistics(data);
        data.clear();

        assertEquals(0, initial.damage(session));
        assertEquals(17, statistics.damage(session));
        assertEquals(20, statistics.credit(session, 3).damage(session));
        assertEquals(17, statistics.damage(session));
        assertSame(statistics, statistics.credit(session, 0));
        assertThrows(UnsupportedOperationException.class, () -> statistics.damage().clear());
        assertEquals(0xffffffffL, initial.credit(session, 0xffffffffL).damage(session));
        assertThrows(IllegalArgumentException.class, () -> initial.credit(session, -1));
        assertThrows(IllegalArgumentException.class, () -> initial.credit(session, 0x100000000L));
        assertThrows(IllegalArgumentException.class, () -> statistics.credit(session, 0xffffffffL));
    }

    @Test
    void countsAcceptedScenesAndCreditsEveryEnemyDeathOnlyOnceIncludingFalls() {
        var session = UUID.randomUUID();
        var original = MatchCombatStatistics.empty();
        var updated = original.fired(session).fired(session).killed(session);
        assertEquals(0, original.acceptedShots(session));
        assertEquals(2, updated.acceptedShots(session));
        assertEquals(1, updated.kills(session));
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchCombatStatistics(
                Map.of(),
                Map.of(
                    session,
                    -1
                ),
                Map.of()
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchCombatStatistics(
                Map.of(),
                Map.of(
                    session,
                    256
                ),
                Map.of()
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchCombatStatistics(
                Map.of(),
                Map.of(),
                Map.of(
                    session,
                    -1L
                )
            )
        );
        assertThrows(
            ArithmeticException.class,
            () -> new MatchCombatStatistics(
                Map.of(),
                Map.of(),
                Map.of(session, Long.MAX_VALUE)
            ).fired(session)
        );

        try (var fixture = new MatchBootstrapFixture(4, RoomMode.ITEM)) {
            fixture.start();
            var snapshot = fixture.snapshot();
            var source = snapshot.participants().getFirst();
            var enemies = snapshot.participants().stream().filter(p -> p.team() != source.team()).toList();
            var ally = snapshot.participants().stream().filter(p -> p.team() == source.team()
                && !p.sessionId().equals(source.sessionId())).findFirst().orElseThrow();
            var draft = new ScenarioWorldDraft(snapshot.world(), snapshot.definitions(), snapshot.participants());
            draft.fired(source.sessionId());
            draft.damage(source.sessionId(), enemies.getFirst().sessionId(), Integer.MAX_VALUE);
            draft.dead(source.sessionId(), enemies.getFirst().sessionId());
            draft.dead(source.sessionId(), enemies.getLast().sessionId());
            draft.dead(source.sessionId(), enemies.getLast().sessionId());
            draft.dead(source.sessionId(), ally.sessionId());
            draft.dead(source.sessionId(), source.sessionId());
            var world = draft.finish(
                new ScenarioCommitReceipt(
                    "kill-test",
                    source.sessionId(),
                    source.admissionId(),
                    1,
                    snapshot.world().revision() + 1
                ),
                Set.of(),
                1000
            );

            assertEquals(2, world.combatStatistics().kills(source.sessionId()));
            assertEquals(1, world.combatStatistics().acceptedShots(source.sessionId()));
            assertEquals(0, snapshot.world().combatStatistics().kills(source.sessionId()));
            assertEquals(world.combatStatistics(), world.withoutBoxes().combatStatistics());
            var next = new ScenarioWorldDraft(world, snapshot.definitions(), snapshot.participants());
            next.dead(source.sessionId(), enemies.getFirst().sessionId());
            var repeated = next.finish(
                new ScenarioCommitReceipt(
                    "repeat",
                    source.sessionId(),
                    source.admissionId(),
                    2,
                    world.revision() + 1
                ),
                Set.of(),
                1000
            );
            assertEquals(2, repeated.combatStatistics().kills(source.sessionId()));
        }
    }

    @Test
    void countsOnlyRemovedEnemyHpAndPreservesTotalsThroughMovementAndCleanup() throws Exception {
        try (var fixture = new MatchBootstrapFixture(4, RoomMode.ITEM)) {
            fixture.start();
            var snapshot = fixture.snapshot();
            var source = snapshot.participants().getFirst();
            var enemy = snapshot.participants().stream().filter(p -> p.team() != source.team())
                .findFirst().orElseThrow();
            var ally = snapshot.participants().stream().filter(p -> p.team() == source.team()
                && !p.sessionId().equals(source.sessionId())).findFirst().orElseThrow();
            var before = snapshot.world();
            var draft = new ScenarioWorldDraft(before, snapshot.definitions(), snapshot.participants());
            draft.damage(source.sessionId(), enemy.sessionId(), 100);
            draft.damage(source.sessionId(), enemy.sessionId(), Integer.MAX_VALUE);
            draft.damage(source.sessionId(), enemy.sessionId(), 50);
            draft.damage(source.sessionId(), source.sessionId(), 10);
            draft.damage(source.sessionId(), ally.sessionId(), 20);
            var receipt = new ScenarioCommitReceipt("damage-test", source.sessionId(), source.admissionId(), 1, 1);
            var committed = draft.finish(receipt, Set.of(), 1000);

            assertEquals(
                before.actors().get(enemy.sessionId()).hp(),
                committed.combatStatistics().damage(source.sessionId())
            );
            assertEquals(0, before.combatStatistics().damage(source.sessionId()));
            assertEquals(0, committed.actors().get(enemy.sessionId()).hp());
            assertEquals(
                before.actors().get(source.sessionId()).hp() - 10,
                committed.actors().get(source.sessionId()).hp()
            );
            assertEquals(
                before.actors().get(ally.sessionId()).hp() - 20,
                committed.actors().get(ally.sessionId()).hp()
            );
            assertEquals(committed.combatStatistics(), committed.withoutBoxes().combatStatistics());
            assertEquals(committed.combatStatistics(), committed.energy(source.sessionId(), 5).combatStatistics());
            assertThrows(
                ScenarioWorldException.class,
                () -> draft.damage(UUID.randomUUID(), enemy.sessionId(), 10)
            );
        }
    }
}
