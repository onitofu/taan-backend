package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.match.service.ScenarioTestWorld;

class PowerQuizStateTest {
    @Test
    void opensOnlyForLiveOpponentsWhoHaveNotLeftAndKeepsTheSourceIdentity() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var snapshot = f.snapshot();
            var shooter = snapshot.participants().getFirst();
            var recipient = snapshot.participants().get(1);
            var world = snapshot.world();
            var prepared = prepared(world, shooter);
            var original = MatchLootState.empty();
            var opened = original.openQuiz(
                snapshot.participants(),
                world,
                snapshot.resolution(),
                snapshot.turn(),
                prepared
            );
            var opportunity = opened.quizOpportunities().get(recipient.slotIndex());

            assertTrue(original.quizOpportunities().isEmpty());
            assertEquals(recipient.sessionId(), opportunity.sessionId());
            assertEquals(recipient.admissionId(), opportunity.admissionId());
            assertEquals(shooter.slotIndex(), opportunity.sourceSlot());
            assertEquals(snapshot.turn().revision(), opportunity.turnRevision());
            assertEquals("scenario", opportunity.scenarioHash());
            assertThrows(UnsupportedOperationException.class, () -> opened.quizOpportunities().clear());
            var departed = new MatchResolutionState(
                Optional.empty(),
                Map.of(recipient.sessionId(), MatchExitKind.DEPARTED)
            );
            assertTrue(original.openQuiz(snapshot.participants(), world, departed, snapshot.turn(), prepared)
                .quizOpportunities().isEmpty());
            var actor = world.actors().get(recipient.sessionId());
            var dead = world.placed(
                recipient.sessionId(),
                new MatchActorState(actor.x(), actor.y(), actor.facing(), 0, actor.energy())
            );
            var ineligible = original.openQuiz(
                snapshot.participants(),
                dead,
                snapshot.resolution(),
                snapshot.turn(),
                prepared
            );
            assertTrue(ineligible.quizOpportunities().isEmpty());
        }
    }

    @Test
    void consumesOnceAndDropsOpportunitiesOnDepartureOrClosureWithoutRemovingLiveItems() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var snapshot = f.snapshot();
            var shooter = snapshot.participants().getFirst();
            var recipient = snapshot.participants().get(1);
            var world = snapshot.world();
            var empty = MatchLootState.empty();
            assertSame(empty, empty.grantQuiz(recipient.slotIndex(), 5));
            assertSame(empty, empty.closeQuiz(world));
            var opened = empty.openQuiz(
                snapshot.participants(),
                world,
                snapshot.resolution(),
                snapshot.turn(),
                prepared(world, shooter)
            );
            var granted = opened.grantQuiz(recipient.slotIndex(), 5);
            assertTrue(granted.quizOpportunities().get(recipient.slotIndex()).consumed());
            assertSame(granted, granted.grantQuiz(recipient.slotIndex(), 7));
            var closed = granted.closeQuiz(world);
            assertTrue(closed.quizOpportunities().isEmpty());
            assertEquals(granted.inventories(), closed.inventories());
            var departed = opened.without(recipient.sessionId());
            assertTrue(departed.quizOpportunities().isEmpty());
            assertSame(departed, departed.without(recipient.sessionId()));
            var actor = world.actors().get(recipient.sessionId());
            var dead = world.placed(
                recipient.sessionId(),
                new MatchActorState(actor.x(), actor.y(), actor.facing(), 0, actor.energy())
            );
            assertTrue(granted.closeQuiz(dead).inventories().isEmpty());
            assertTrue(granted.without(recipient.sessionId()).inventories().isEmpty());
        }
    }

    private MatchWorldState prepared(MatchWorldState world, MatchParticipant shooter) {
        return new MatchWorldState(
            1,
            world.actors(),
            world.terrain(),
            Map.of(),
            Set.of(),
            Optional.of(new ScenarioCommitReceipt("scenario", shooter.sessionId(), shooter.admissionId(), 1, 1))
        );
    }
}
