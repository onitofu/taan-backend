package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.ReportMatchBonusCommand;

final class MatchLootFixture implements AutoCloseable {
    final MovementRuntimeFixture movement = new MovementRuntimeFixture(new MatchLootPolicy(Set.of(1, 5, 7, 1005)));
    final MatchRuntimeFixture f = movement.f;

    MatchLootFixture() throws IOException {
    }

    MatchCommandContext context(int slot) {
        var participant = f.runtime.snapshot().participants().stream().filter(p -> p.slotIndex() == slot)
            .findFirst().orElseThrow();
        return MatchCommandContext.from(f.runtime.snapshot(), participant.sessionId());
    }

    ReportMatchBonusCommand bonus(int slot, int item) {
        return new ReportMatchBonusCommand(context(slot), slot, slot, OptionalInt.empty(), -1L, item, -17, -3, -1);
    }

    boolean report(int slot, int item) {
        return complete(MatchItemExecution.reportBonus(f.runtime, bonus(slot, item)));
    }

    boolean confirm(int slot, int index, int item) {
        return complete(MatchItemExecution.confirmItemReceipt(
            f.runtime,
            new ConfirmMatchItemReceiptCommand(context(slot), slot, index, item)
        ));
    }

    boolean discard(int slot, int index, int item) {
        return complete(MatchItemExecution.discardItem(
            f.runtime,
            new DiscardMatchItemCommand(context(slot), slot, index, item)
        ));
    }

    void pass() {
        assertTrue(movement.pass(movement.pass()));
    }

    void offerOwner() {
        if (f.runtime.snapshot().turn().activeSlot() != 0) {
            pass();
        }

        pass();
    }

    boolean complete(CompletionStage<Boolean> stage) {
        f.run();
        return stage.toCompletableFuture().join();
    }

    @Override
    public void close() {
        movement.close();
    }
}
