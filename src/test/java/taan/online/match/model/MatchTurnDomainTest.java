package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.MatchRoomFixture;
import taan.online.room.model.RoomMode;

class MatchTurnDomainTest {
    @Test
    void advancesPastDeadActorsAfterAllAcknowledgementsWhileRetainingTheCanonicalWorld() {
        try (var fixture = new MatchRoomFixture(4, RoomMode.ITEM)) {
            var s = fixture.scenario.matches.current(fixture.scenario.rooms.reserveMatch(fixture.owner).token())
                .orElseThrow();
            var match = new Match(
                s.id(),
                s.roomId(),
                s.roomGeneration(),
                s.channelId(),
                s.ownerSessionId(),
                s.mode(),
                s.rule(),
                s.definitions(),
                s.participants(),
                s.captains(),
                0
            );
            match.commit(0, 30);

            for (var p : s.participants()) {
                match.loadingDone(p.sessionId(), p.slotIndex(), p.spawn().settledX(), p.spawn().settledY());
                match.deliveryCompleted(p.sessionId());
            }

            match.beginSynchronization(0);
            s.participants().forEach(p -> match.deliveryCompleted(p.sessionId()));
            match.actionStarted(0, 20);
            var shooter = s.participants().getFirst();
            var dead = s.participants().get(1);
            var draft = new ScenarioWorldDraft(match.world(), s.definitions(), s.participants());
            draft.dead(dead.sessionId());
            var receipt = new ScenarioCommitReceipt(
                "a".repeat(64),
                shooter.sessionId(),
                shooter.admissionId(),
                match.turn().revision(),
                match.world().revision() + 1
            );
            var prepared = draft.finish(receipt, Set.of(), 0);
            var pending = new MatchCompletion(
                shooter.sessionId(),
                shooter.admissionId(),
                receipt.fingerprint(),
                receipt.turnRevision(),
                receipt.worldRevision(),
                Set.of(shooter.sessionId()),
                0,
                40
            );
            assertTrue(match.commitScenario(match.world().revision(), 1, prepared, Optional.of(pending)));
            assertTrue(match.acknowledgeScenario(shooter.sessionId()));
            assertTrue(match.nextTurn(1, 20));
            assertEquals(2, match.turn().activeSlot());
            assertEquals(0, match.actors().get(dead.sessionId()).hp());
            assertFalse(match.turn().remainingThisRound().contains(dead.slotIndex()));
        }
    }
}
