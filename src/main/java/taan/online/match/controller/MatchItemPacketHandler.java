package taan.online.match.controller;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.match.protocol.codec.UseItemCodec;
import taan.online.match.protocol.codec.SpendItemCodec;
import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.match.service.ActivateSpecialAction;
import taan.online.match.service.RecordMatchSpendAction;
import taan.online.match.service.model.ActivateSpecialCommand;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.match.service.model.SpentMatchItem;
import taan.online.match.service.model.MatchCommandContext;

public final class MatchItemPacketHandler {
    private final ActivateSpecialAction activation;
    private final RecordMatchSpendAction spending;

    public MatchItemPacketHandler(ActivateSpecialAction activation, RecordMatchSpendAction spending) {
        this.activation = activation;
        this.spending = spending;
    }

    public CompletionStage<Void> handle(UUID id, int type, byte[] root, MatchRequestContext request) {
        var context = request.roomContext();

        if (context == null || !context.membership().sessionId().equals(id)) {
            return CompletableFuture.completedFuture(null);
        }

        var sender = MatchCommandContext.from(context);

        if (type == PcGamePlayUseItem.TYPE_ID) {
            var packet = new UseItemCodec().decode(root);
            return activation.execute(new ActivateSpecialCommand(
                sender,
                request.turnRevision(),
                request.phaseRevision(),
                packet.slotIndex(),
                packet.team(),
                packet.flag12(),
                packet.flag13(),
                packet.inventoryIndex(),
                packet.itemId()
            )).thenApply(ignored -> null);
        }

        var packet = new SpendItemCodec().decode(root);
        return spending.execute(new RecordMatchSpendCommand(
            sender,
            request.turnRevision(),
            request.phaseRevision(),
            packet.slotIndex(),
            packet.teamClear11(),
            packet.teamClear12(),
            packet.items().stream().map(item -> new SpentMatchItem(item.itemId(), item.scalar2())).toList()
        ))
            .thenApply(ignored -> null);
    }
}
