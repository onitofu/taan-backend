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

class MatchDamageStatisticsTest {
    @Test
    void keepsImmutableTotalsAndRejectsOverflowInsteadOfWrapping() {
        var session = UUID.randomUUID();
        var initial = MatchDamageStatistics.empty();
        var data = new HashMap<>(Map.of(session, 17L));
        var statistics = new MatchDamageStatistics(data);
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
                committed.damageStatistics().damage(source.sessionId())
            );
            assertEquals(0, before.damageStatistics().damage(source.sessionId()));
            assertEquals(0, committed.actors().get(enemy.sessionId()).hp());
            assertEquals(
                before.actors().get(source.sessionId()).hp() - 10,
                committed.actors().get(source.sessionId()).hp()
            );
            assertEquals(
                before.actors().get(ally.sessionId()).hp() - 20,
                committed.actors().get(ally.sessionId()).hp()
            );
            assertEquals(committed.damageStatistics(), committed.withoutBoxes().damageStatistics());
            assertEquals(committed.damageStatistics(), committed.energy(source.sessionId(), 5).damageStatistics());
            assertThrows(
                ScenarioWorldException.class,
                () -> draft.damage(UUID.randomUUID(), enemy.sessionId(), 10)
            );
        }
    }
}
