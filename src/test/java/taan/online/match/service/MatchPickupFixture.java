package taan.online.match.service;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchLootPolicy;

final class MatchPickupFixture implements AutoCloseable {
    static final MatchLootPolicy POLICY = new MatchLootPolicy(
        Set.of(1, 5, 7, 1005, 1010, 1015, 1020, 1025, 1030, 1035, 1040, 1045, 1050)
    );
    final MatchBoxFixture boxes = new MatchBoxFixture(POLICY);
    final MatchRuntimeFixture f = boxes.f;

    MatchPickupFixture() throws IOException {
    }

    ScenarioEatItemBoxCommand pickup(long id, int tick, int item, int draw) {
        return new ScenarioEatItemBoxCommand(
            new ScenarioCommandHeader(f.runtime.snapshot().turn().activeSlot(), 11, tick, 0),
            id,
            item,
            draw
        );
    }

    boolean confirm(int index, int item) {
        var context = context();
        var pending = MatchItemExecution.confirmItemReceipt(
            f.runtime,
            new ConfirmMatchItemReceiptCommand(context, f.runtime.snapshot().turn().activeSlot(), index, item)
        );
        f.run();
        return pending.toCompletableFuture().join();
    }

    boolean discard(int index, int item) {
        var context = context();
        var pending = MatchItemExecution.discardItem(
            f.runtime,
            new DiscardMatchItemCommand(context, f.runtime.snapshot().turn().activeSlot(), index, item)
        );
        f.run();
        return pending.toCompletableFuture().join();
    }

    MatchCommandContext context() {
        var snapshot = f.runtime.snapshot();
        var actor = snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
            .findFirst().orElseThrow();
        return MatchCommandContext.from(snapshot, actor.sessionId());
    }

    List<Integer> slots() {
        return f.runtime.snapshot().loot().inventory(context().sessionId()).slots();
    }

    @Override
    public void close() {
        boxes.close();
    }
}
