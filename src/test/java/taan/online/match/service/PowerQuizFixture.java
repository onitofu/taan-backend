package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.OptionalInt;
import java.util.concurrent.CompletionStage;
import taan.online.match.model.MatchBoxPolicy;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.ReportMatchBonusCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.model.RoomRule;

final class PowerQuizFixture implements AutoCloseable {
    final LiveMatchFixture live;
    final MatchRuntimeFixture f;

    PowerQuizFixture() throws IOException {
        this(MatchLootPolicy.nativeItems());
    }

    PowerQuizFixture(MatchLootPolicy policy) throws IOException {
        live = new LiveMatchFixture(policy, MatchBoxPolicy.DISABLED, RoomRule.POWER_QUIZ);
        f = live.f;
    }

    MatchCommandContext context(int slot) {
        var snapshot = f.runtime.snapshot();
        var participant = snapshot.participants().stream().filter(member -> member.slotIndex() == slot)
            .findFirst().orElseThrow();
        return MatchCommandContext.from(snapshot, participant.sessionId());
    }

    ReportMatchBonusCommand claim(int slot, int item) {
        return new ReportMatchBonusCommand(context(slot), slot, slot, OptionalInt.empty(), -1L, item, -17, -3, -1);
    }

    boolean report(ReportMatchBonusCommand command) {
        return complete(MatchItemExecution.reportBonus(f.runtime, command));
    }

    boolean confirm(int slot, int index, int item) {
        return complete(MatchItemExecution.confirmItemReceipt(
            f.runtime,
            new ConfirmMatchItemReceiptCommand(context(slot), slot, index, item)
        ));
    }

    boolean complete(CompletionStage<Boolean> stage) {
        f.run();
        return stage.toCompletableFuture().join();
    }

    void shoot(boolean lethal) throws IOException {
        var applied = f.runtime.applyScenario(live.shot(false, lethal));
        f.run();
        assertEquals(ScenarioApplicationOutcome.APPLIED, applied.join().outcome());
    }

    void finishScenario() {
        f.timers.now = f.runtime.snapshot().completion().orElseThrow().settleNotBeforeNanos();
        assertTrue(complete(f.runtime.confirmScenario(live.ack(0, false))));
        assertTrue(complete(f.runtime.confirmScenario(live.ack(1, false))));
    }

    @Override
    public void close() {
        live.close();
    }
}
