package taan.online.match.controller;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.match.protocol.codec.GiveUpCodec;
import taan.online.match.protocol.codec.LoadingDoneCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.ReportEnemiesCodec;
import taan.online.match.protocol.codec.EndScenarioCodec;
import taan.online.match.protocol.codec.UnsupportedGameplayCodec;
import taan.online.match.protocol.packet.PcGamePlayLoadingDone;
import taan.online.match.protocol.packet.PcGamePlaySetPos;
import taan.online.match.protocol.packet.PqGameSessionReportEnemies;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.service.ConfirmMatchLoadingAction;
import taan.online.match.service.ConfirmMatchPositionAction;
import taan.online.match.service.ReportMatchEnemiesAction;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.match.protocol.codec.ResultSelectionCodec;
import taan.online.match.protocol.packet.PcGamePlayGiveUp;
import taan.online.match.protocol.packet.PqResultRandSelect;
import taan.online.match.protocol.packet.PqResultSelectCard;
import taan.online.match.service.ConsumeMatchResultAction;
import taan.online.match.service.MatchRegistry;
import taan.online.match.service.TerminateMatchAction;
import taan.online.match.view.ProtocolMatchView;
import taan.online.protocol.MalformedPacketException;
import taan.online.protocol.PacketOutput;
import taan.online.room.service.RoomMatchAccess;

public final class MatchPacketHandler {
    private final RoomMatchAccess rooms;
    private final MatchRegistry matches;
    private final TerminateMatchAction terminate;
    private final ConsumeMatchResultAction consume;
    private final ConfirmMatchLoadingAction loading;
    private final ConfirmMatchPositionAction position;
    private final ReportMatchEnemiesAction reports;

    public MatchPacketHandler(
        RoomMatchAccess rooms,
        MatchRegistry matches,
        TerminateMatchAction terminate,
        ConsumeMatchResultAction consume,
        ConfirmMatchLoadingAction loading,
        ConfirmMatchPositionAction position,
        ReportMatchEnemiesAction reports
    ) {
        this.rooms = rooms;
        this.matches = matches;
        this.terminate = terminate;
        this.consume = consume;
        this.loading = loading;
        this.position = position;
        this.reports = reports;
    }

    public void open(UUID id, PacketOutput output) {
        matches.subscribe(id, new ProtocolMatchView(output));
    }

    public void close(UUID id) {
        matches.unsubscribe(id);
    }

    public MatchRequestContext capture(UUID id) {
        return new MatchRequestContext(rooms.matchContext(id));
    }

    public CompletionStage<Void> handle(UUID id, int type, byte[] root, MatchRequestContext request) {
        var context = request == null ? null : request.roomContext();

        if (type == PcGamePlayGiveUp.TYPE_ID) {
            new GiveUpCodec().decode(root);
            return terminate.execute(id, context).delivery();
        }

        if (type == PcGamePlayLoadingDone.TYPE_ID) {
            var message = new LoadingDoneCodec().decode(root);

            if (message.positions().size() != 1) {
                throw new MalformedPacketException("INVALID_LOCAL_SLOT_COUNT");
            }

            var loaded = message.positions().getFirst();
            return loading.execute(id, context, loaded.slotIndex(), loaded.x(), loaded.y());
        }

        if (type == PcGamePlaySetPos.TYPE_ID) {
            var message = new SetPosCodec().decode(root);
            position.execute(id, context, message.slotIndex(), message.x(), message.y(), message.facing());
            return CompletableFuture.completedFuture(null);
        }

        if (type == PqGameSessionReportEnemies.TYPE_ID) {
            new ReportEnemiesCodec().decode(root);
            reports.execute(id, context);
            return CompletableFuture.completedFuture(null);
        }

        if (type == PcGamePlayEndScenario.TYPE_ID) {
            new EndScenarioCodec().decode(root);
            return CompletableFuture.completedFuture(null);
        }

        if (UnsupportedGameplayCodec.accepts(type)) {
            new UnsupportedGameplayCodec().decode(type, root);
            return terminate.execute(id, context, RoomMatchEndReason.UNSUPPORTED_GAMEPLAY).delivery();
        }

        var codec = new ResultSelectionCodec();

        if (type == PqResultRandSelect.TYPE_ID) {
            codec.randomSelect(root);
        } else if (type == PqResultSelectCard.TYPE_ID) {
            codec.selectCard(root);
        } else {
            throw new MalformedPacketException("UNSUPPORTED_MATCH_PACKET");
        }

        consume.execute(id, context);
        return CompletableFuture.completedFuture(null);
    }
}
