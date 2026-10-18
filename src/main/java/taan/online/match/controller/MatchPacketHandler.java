package taan.online.match.controller;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.match.protocol.codec.GiveUpCodec;
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

    public MatchPacketHandler(
        RoomMatchAccess rooms,
        MatchRegistry matches,
        TerminateMatchAction terminate,
        ConsumeMatchResultAction consume
    ) {
        this.rooms = rooms;
        this.matches = matches;
        this.terminate = terminate;
        this.consume = consume;
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
