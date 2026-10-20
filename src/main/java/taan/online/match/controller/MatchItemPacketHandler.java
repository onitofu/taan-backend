package taan.online.match.controller;

import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.match.protocol.codec.EatBonusItemCodec;
import taan.online.match.protocol.codec.EatItemBoxCodec;
import taan.online.match.protocol.codec.ForsakeItemCodec;
import taan.online.match.protocol.codec.NotifyEatItemCodec;
import taan.online.match.protocol.codec.SpendItemCodec;
import taan.online.match.protocol.codec.UseItemCodec;
import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;
import taan.online.match.protocol.packet.PcGamePlayEatItemBox;
import taan.online.match.protocol.packet.PcGamePlayForsakeItem;
import taan.online.match.protocol.packet.PcGamePlayNotifyEatItem;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.match.service.ActivateSpecialAction;
import taan.online.match.service.ConfirmMatchItemReceiptAction;
import taan.online.match.service.DiscardMatchItemAction;
import taan.online.match.service.RecordMatchSpendAction;
import taan.online.match.service.ReportMatchBonusAction;
import taan.online.match.service.ReportMatchPickupAction;
import taan.online.match.service.model.ActivateSpecialCommand;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.match.service.model.ReportMatchBonusCommand;
import taan.online.match.service.model.ReportMatchPickupCommand;
import taan.online.match.service.model.SpentMatchItem;
import taan.online.protocol.MalformedPacketException;

public final class MatchItemPacketHandler {
    private final ActivateSpecialAction activation;
    private final RecordMatchSpendAction spending;
    private final ReportMatchBonusAction bonuses;
    private final ReportMatchPickupAction pickups;
    private final ConfirmMatchItemReceiptAction receipts;
    private final DiscardMatchItemAction discards;

    public MatchItemPacketHandler(
        ActivateSpecialAction activation,
        RecordMatchSpendAction spending,
        ReportMatchBonusAction bonuses,
        ReportMatchPickupAction pickups,
        ConfirmMatchItemReceiptAction receipts,
        DiscardMatchItemAction discards
    ) {
        this.activation = activation;
        this.spending = spending;
        this.bonuses = bonuses;
        this.pickups = pickups;
        this.receipts = receipts;
        this.discards = discards;
    }

    public CompletionStage<Void> handle(UUID id, int type, byte[] root, MatchRequestContext request) {
        var context = request == null ? null : request.roomContext();

        if (context == null || !context.membership().sessionId().equals(id)) {
            return CompletableFuture.completedFuture(null);
        }

        var sender = MatchCommandContext.from(context);

        if (type == PcGamePlayEatBonusItem.TYPE_ID) {
            var packet = new EatBonusItemCodec().decode(root);
            var command = new ReportMatchBonusCommand(
                sender,
                packet.slotIndex(),
                packet.command().header().slot(),
                packet.gauge().map(value -> OptionalInt.of(value.header().slot())).orElse(OptionalInt.empty()),
                packet.command().entityId()
            );

            return bonuses.execute(command).thenApply(ignored -> null);
        }

        if (type == PcGamePlayEatItemBox.TYPE_ID) {
            var packet = new EatItemBoxCodec().decode(root);
            var command = new ReportMatchPickupCommand(
                sender,
                packet.slotIndex(),
                packet.command().header().slot(),
                packet.gauge().map(value -> OptionalInt.of(value.header().slot())).orElse(OptionalInt.empty()),
                packet.command().entityId(),
                packet.command().itemId()
            );

            return pickups.execute(command).thenApply(ignored -> null);
        }

        if (type == PcGamePlayNotifyEatItem.TYPE_ID) {
            var packet = new NotifyEatItemCodec().decode(root);
            var command = new ConfirmMatchItemReceiptCommand(
                sender,
                packet.slotIndex(),
                packet.inventoryIndex(),
                packet.itemId()
            );

            return receipts.execute(command).thenApply(ignored -> null);
        }

        if (type == PcGamePlayForsakeItem.TYPE_ID) {
            var packet = new ForsakeItemCodec().decode(root);
            var command = new DiscardMatchItemCommand(
                sender,
                packet.slotIndex(),
                packet.inventoryIndex(),
                packet.itemId()
            );

            return discards.execute(command).thenApply(ignored -> null);
        }

        if (type == PcGamePlayUseItem.TYPE_ID) {
            var packet = new UseItemCodec().decode(root);
            var command = new ActivateSpecialCommand(
                sender,
                request.turnRevision(),
                request.phaseRevision(),
                packet.slotIndex(),
                packet.team(),
                packet.flag12(),
                packet.flag13(),
                packet.inventoryIndex(),
                packet.itemId()
            );

            return activation.execute(command).thenApply(ignored -> null);
        }

        if (type != PcGamePlaySpendItem.TYPE_ID) {
            throw new MalformedPacketException("UNSUPPORTED_MATCH_ITEM_PACKET");
        }

        var packet = new SpendItemCodec().decode(root);
        var command = new RecordMatchSpendCommand(
            sender,
            request.turnRevision(),
            request.phaseRevision(),
            packet.slotIndex(),
            packet.teamClear11(),
            packet.teamClear12(),
            packet.items().stream().map(item -> new SpentMatchItem(item.itemId(), item.scalar2())).toList()
        );

        return spending.execute(command).thenApply(ignored -> null);
    }
}
