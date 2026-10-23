package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.match.model.BattleInventory;
import taan.online.match.model.MatchItemReceipt;
import taan.online.match.model.MatchItemSource;
import taan.online.match.model.MatchLootState;
import taan.online.match.model.PowerQuizOpportunity;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.ReportMatchBonusCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;

class PowerQuizTest {
    @Test
    void awardsBeforeAcknowledgementsOnlyOnceAndAllowsAnotherWinOnTheNextEnemyShot() throws Exception {
        try (var quiz = new PowerQuizFixture(); var logs = new LogCapture()) {
            assertFalse(quiz.report(quiz.claim(2, 5)));
            var shot = quiz.live.shot(false, false);
            var applied = quiz.f.runtime.applyScenario(shot);
            quiz.f.run();
            assertEquals(ScenarioApplicationOutcome.APPLIED, applied.join().outcome());
            var before = quiz.f.runtime.snapshot();
            var opportunity = before.loot().quizOpportunities().get(2);
            assertEquals(0, opportunity.sourceSlot());
            assertEquals(0, opportunity.pickerScalar());
            assertFalse(opportunity.consumed());
            assertFalse(quiz.report(quiz.claim(0, 5)));

            assertTrue(quiz.report(quiz.claim(2, 5)));
            var granted = quiz.f.runtime.snapshot();
            var receipt = granted.loot().inventory(quiz.context(2).sessionId()).receipts().getFirst();
            assertEquals(MatchItemSource.POWER_QUIZ, receipt.source());
            assertEquals(Optional.of(opportunity.scenarioHash()), receipt.scenarioHash());
            assertEquals(List.of(5, 65535, 65535), granted.loot().inventory(quiz.context(2).sessionId()).slots());
            assertSame(before.world(), granted.world());
            assertEquals(1, quiz.f.owner.bonuses.size());
            assertEquals(quiz.f.owner.bonuses, quiz.f.guest.bonuses);
            assertFalse(quiz.report(quiz.claim(2, 5)));
            var repeated = quiz.f.runtime.applyScenario(shot);
            quiz.f.run();
            assertEquals(ScenarioApplicationOutcome.DUPLICATE, repeated.join().outcome());
            assertFalse(quiz.report(quiz.claim(2, 7)));
            assertEquals(1, quiz.f.owner.bonuses.size());
            assertTrue(quiz.confirm(2, 0, 5));
            assertFalse(quiz.confirm(2, 0, 5));
            quiz.finishScenario();

            assertTrue(quiz.f.runtime.snapshot().loot().quizOpportunities().isEmpty());
            assertTrue(quiz.f.runtime.snapshot().loot().offers().isEmpty());
            assertFalse(quiz.report(quiz.claim(2, 7)));
            quiz.shoot(false);
            assertTrue(quiz.report(quiz.claim(0, 7)));
            assertTrue(quiz.confirm(0, 0, 7));
            quiz.finishScenario();
            quiz.shoot(false);
            assertTrue(quiz.report(quiz.claim(2, 1005)));
            assertTrue(quiz.confirm(2, 1, 1005));
            assertEquals(List.of(5, 1005, 65535), quiz.f.runtime.snapshot().loot()
                .inventory(quiz.context(2).sessionId()).slots());
            assertEquals(3, quiz.f.owner.bonuses.size());
            assertTrue(logs.text().contains("match.powerquiz.opportunity.opened"));
            assertTrue(logs.text().contains("match.powerquiz.opportunity.closed"));
            assertTrue(logs.text().contains("match.powerquiz.grant.accepted"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains("opaque24"));
        }
    }

    @Test
    void consumesAFullInventoryWinWithoutEchoAndCannotRetryAfterDiscard() throws Exception {
        try (var quiz = new PowerQuizFixture()) {
            var context = quiz.context(2);
            var loot = quiz.f.match.loot();

            for (int item : new int[]{1, 5, 7}) {
                loot = loot.inserted(
                    context.sessionId(),
                    context.admissionId(),
                    1,
                    item,
                    MatchItemSource.PROJECTILE_PICKUP,
                    OptionalLong.of(item),
                    Optional.empty()
                ).confirmed(
                    context.sessionId(),
                    context.admissionId(),
                    loot.inventory(context.sessionId()).firstEmpty(),
                    item
                );
            }

            quiz.f.match.updateLoot(loot);
            quiz.f.runtime.refresh();
            quiz.shoot(false);
            var before = quiz.f.runtime.snapshot();
            assertTrue(quiz.report(quiz.claim(2, 1005)));
            assertEquals(before.loot().inventories(), quiz.f.runtime.snapshot().loot().inventories());
            assertEquals(before.loot().grantSequence(), quiz.f.runtime.snapshot().loot().grantSequence());
            assertTrue(quiz.f.owner.bonuses.isEmpty());
            assertTrue(quiz.f.runtime.snapshot().loot().quizOpportunities().get(2).consumed());
            assertTrue(quiz.complete(MatchItemExecution.discardItem(
                quiz.f.runtime,
                new DiscardMatchItemCommand(context, 2, 0, 1)
            )));
            assertFalse(quiz.report(quiz.claim(2, 1005)));
            assertEquals(65535, quiz.f.runtime.snapshot().loot().inventory(context.sessionId()).slots().getFirst());
            assertTrue(quiz.f.guest.bonuses.isEmpty());
        }
    }

    @Test
    void rejectsForgedSlotsGaugeUnsupportedRewardsAndExpiredOrForeignClaims() throws Exception {
        try (var quiz = new PowerQuizFixture()) {
            quiz.shoot(false);
            var claim = quiz.claim(2, 5);
            var context = claim.context();
            var before = quiz.f.runtime.snapshot();

            for (var invalid : List.of(
                new ReportMatchBonusCommand(context, 0, 0, OptionalInt.empty(), -1L, 5, 0, 0, 0),
                new ReportMatchBonusCommand(context, 2, 0, OptionalInt.empty(), -1L, 5, 0, 0, 0),
                new ReportMatchBonusCommand(context, 2, 2, OptionalInt.empty(), 1L, 5, 0, 0, 0),
                new ReportMatchBonusCommand(context, 2, 2, OptionalInt.of(2), -1L, 5, 0, 0, 0),
                quiz.claim(2, 0),
                quiz.claim(2, 4),
                new ReportMatchBonusCommand(
                    new MatchCommandContext(
                        context.matchId(),
                        context.roomId(),
                        context.roomGeneration(),
                        context.channelId() + 1,
                        context.sessionId(),
                        context.admissionId()
                    ),
                    2,
                    2,
                    OptionalInt.empty(),
                    -1L,
                    5,
                    0,
                    0,
                    0
                )
            )) {
                assertFalse(quiz.report(invalid));
                assertSame(before, quiz.f.runtime.snapshot());
            }

            quiz.f.admitted = false;
            assertFalse(quiz.report(claim));
            quiz.f.admitted = true;
            quiz.f.timers.now = before.completion().orElseThrow().deadlineNanos();
            assertFalse(quiz.report(claim));
            assertSame(before, quiz.f.runtime.snapshot());
            assertTrue(quiz.f.owner.bonuses.isEmpty());
        }

        try (var quiz = new PowerQuizFixture(MatchLootPolicy.disabled())) {
            quiz.shoot(false);
            assertFalse(quiz.report(quiz.claim(2, 5)));
            assertTrue(quiz.f.guest.bonuses.isEmpty());
        }
    }

    @Test
    void preservesPreScenarioEligibilityForALethalShotAndClearsTheDeadInventoryAtCompletion() throws Exception {
        try (var quiz = new PowerQuizFixture()) {
            quiz.shoot(true);
            var recipient = quiz.context(2).sessionId();
            assertTrue(quiz.f.runtime.snapshot().actors().get(recipient).dead());
            assertTrue(quiz.report(quiz.claim(2, 5)));
            assertTrue(quiz.confirm(2, 1, 5));
            assertFalse(quiz.f.match.loot().inventory(recipient).receipts().getFirst().confirmed());
            assertTrue(quiz.confirm(2, 0, 5));
            assertTrue(quiz.f.match.loot().inventory(recipient).receipts().getFirst().confirmed());
            quiz.finishScenario();

            assertTrue(quiz.f.match.loot().quizOpportunities().isEmpty());
            assertTrue(quiz.f.match.loot().inventories().isEmpty());
            assertFalse(quiz.report(quiz.claim(2, 5)));
        }
    }

    @Test
    void fencesStaleAdmissionScenarioTurnAndPhaseEvenIfAnOldOpportunitySurvives() throws Exception {
        try (var quiz = new PowerQuizFixture()) {
            quiz.shoot(false);
            var snapshot = quiz.f.runtime.snapshot();
            var original = snapshot.loot();
            var current = original.quizOpportunities().get(2);

            for (int changed = 0; changed < 3; changed++) {
                var stale = new PowerQuizOpportunity(
                    current.sessionId(),
                    changed == 0 ? UUID.randomUUID() : current.admissionId(),
                    current.slotIndex(),
                    current.sourceSlot(),
                    current.turnRevision() + (changed == 1 ? 1 : 0),
                    changed == 2 ? "stale" : current.scenarioHash(),
                    current.pickerScalar(),
                    false
                );
                quiz.f.match.updateLoot(new MatchLootState(
                    original.inventories(),
                    original.offers(),
                    Map.of(2, stale),
                    original.grantSequence()
                ));
                quiz.f.runtime.refresh();
                assertFalse(quiz.report(quiz.claim(2, 5)));
                assertTrue(quiz.f.owner.bonuses.isEmpty());
            }

            quiz.f.match.updateLoot(original);
            quiz.f.runtime.refresh();
            quiz.finishScenario();
            quiz.f.match.updateLoot(original);
            quiz.f.runtime.refresh();
            assertFalse(quiz.report(quiz.claim(2, 5)));
            assertTrue(quiz.f.owner.bonuses.isEmpty());
        }
    }

    @Test
    void limitsTheDeadActorReceiptExceptionToTheCurrentQuizSourceAndWindow() throws Exception {
        try (var quiz = new PowerQuizFixture()) {
            quiz.shoot(true);
            assertTrue(quiz.report(quiz.claim(2, 5)));
            var original = quiz.f.match.loot();
            var recipient = quiz.context(2).sessionId();
            var inventory = original.inventory(recipient);
            var receipt = inventory.receipts().getFirst();

            for (boolean wrongSource : new boolean[]{false, true}) {
                var staleReceipt = new MatchItemReceipt(
                    receipt.grantSequence(),
                    receipt.turnRevision(),
                    receipt.inventoryIndex(),
                    receipt.itemId(),
                    false,
                    receipt.admissionId(),
                    wrongSource ? MatchItemSource.END_TURN_BONUS : MatchItemSource.POWER_QUIZ,
                    receipt.boxEntityId(),
                    wrongSource ? receipt.scenarioHash() : Optional.of("stale")
                );
                quiz.f.match.updateLoot(new MatchLootState(
                    Map.of(recipient, new BattleInventory(inventory.slots(), List.of(staleReceipt))),
                    original.offers(),
                    original.quizOpportunities(),
                    original.grantSequence()
                ));
                quiz.f.runtime.refresh();
                assertTrue(quiz.confirm(2, 0, 5));
                assertFalse(quiz.f.match.loot().inventory(recipient).receipts().getFirst().confirmed());
            }

            quiz.f.match.updateLoot(new MatchLootState(
                original.inventories(),
                original.offers(),
                Map.of(),
                original.grantSequence()
            ));
            quiz.f.runtime.refresh();
            assertTrue(quiz.confirm(2, 0, 5));
            assertFalse(quiz.f.match.loot().inventory(recipient).receipts().getFirst().confirmed());
            quiz.f.match.updateLoot(original);
            quiz.f.runtime.refresh();
            quiz.f.timers.now = quiz.f.runtime.snapshot().deadlineNanos();
            assertTrue(quiz.confirm(2, 0, 5));
            assertFalse(quiz.f.match.loot().inventory(recipient).receipts().getFirst().confirmed());
        }
    }

}
