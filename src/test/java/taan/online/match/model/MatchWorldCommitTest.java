package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.match.service.ScenarioTestWorld;

class MatchWorldCommitTest {
    @Test
    void commitsACompleteWorldOnlyAtItsCurrentPhaseTurnRevisionAndAuthenticatedReceipt() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var s = f.snapshot();
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
            var initial = match.world();
            assertFalse(match.actionStarted(0, 20));
            assertFalse(match.commitScenario(0, 1, initial));
            match.commit(0, 30);

            for (var member : match.participants()) {
                match.loadingDone(
                    member.sessionId(),
                    member.slotIndex(),
                    member.spawn().settledX(),
                    member.spawn().settledY()
                );
                match.deliveryCompleted(member.sessionId());
            }

            match.beginSynchronization(0);
            assertFalse(match.actionStarted(0, 20));

            for (var member : match.participants()) {
                match.deliveryCompleted(member.sessionId());
            }

            assertTrue(match.actionStarted(0, 20));
            assertFalse(match.actionStarted(0, 20));
            var before = match.world();
            long revision = before.revision();
            var member = match.participants().getFirst();
            var receipt = new ScenarioCommitReceipt(
                "a".repeat(64),
                member.sessionId(),
                member.admissionId(),
                1,
                revision + 1
            );
            var prepared = new ScenarioWorldDraft(
                before,
                s.definitions(),
                s.participants()
            ).finish(receipt, Set.of(), 0);

            assertFalse(match.commitScenario(revision - 1, 1, prepared));
            assertFalse(match.commitScenario(revision, 2, prepared));
            assertFalse(match.commitScenario(revision, 1, before));

            for (var invalid : new ScenarioCommitReceipt[]{
                null,
                new ScenarioCommitReceipt(receipt.fingerprint(), member.sessionId(), member.admissionId(), 1, 99),
                new ScenarioCommitReceipt(
                    receipt.fingerprint(),
                    member.sessionId(),
                    member.admissionId(),
                    2,
                    revision + 1
                ),
                new ScenarioCommitReceipt(
                    receipt.fingerprint(),
                    UUID.randomUUID(),
                    member.admissionId(),
                    1,
                    revision + 1
                ),
                new ScenarioCommitReceipt(receipt.fingerprint(), member.sessionId(), UUID.randomUUID(), 1, revision + 1)
            }) {
                var rejected = new MatchWorldState(
                    revision + 1,
                    prepared.actors(),
                    prepared.terrain(),
                    prepared.falls(),
                    prepared.processedDeaths(),
                    Optional.ofNullable(invalid)
                );
                assertFalse(match.commitScenario(revision, 1, rejected));
                assertSame(before, match.world());
            }

            assertTrue(match.commitScenario(revision, 1, prepared));
            assertSame(prepared, match.world());
            assertEquals(prepared.actors(), match.actors());
            assertEquals(MatchPhase.COMPLETION, match.phase());
            assertFalse(match.commitScenario(revision, 1, prepared));
        }
    }
}
