package taan.online.lobby.view;

import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.outcome;
import static taan.online.logging.LogField.recipientSessionId;
import static taan.online.logging.LogField.roomCount;
import static taan.online.logging.LogField.roomId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.channel.model.ChannelParticipant;
import taan.online.channel.service.ChannelPresenceOutput;
import taan.online.character.model.CharacterId;
import taan.online.lobby.logging.LobbyLogEvent;
import taan.online.lobby.protocol.codec.LobbyResponseCodec;
import taan.online.lobby.protocol.codec.LobbyRoomListBatchCodec;
import taan.online.lobby.protocol.packet.PcLobbyInitUserList;
import taan.online.lobby.protocol.packet.PcLobbyUpdateUserList;
import taan.online.lobby.protocol.packet.PrLobbyEnter;
import taan.online.lobby.protocol.packet.PrLobbyLeave;
import taan.online.lobby.protocol.packet.UserSimpleInfo;
import taan.online.lobby.service.LobbyRoomListingAccess;
import taan.online.lobby.service.model.LobbyRoomListSnapshot;
import taan.online.logging.EventLogger;
import taan.online.protocol.PacketOutput;
import taan.online.room.protocol.packet.GameSessionInfoPack;

public final class ProtocolLobbyView implements ChannelPresenceOutput {
    private static final EventLogger LOG = EventLogger.forClass(ProtocolLobbyView.class);

    private final PacketOutput output;
    private final UUID sessionId;
    private final LobbyRoomListingAccess rooms;
    private final LobbyRoomListBatchCodec roomCodec;
    private final LobbyResponseCodec codec = new LobbyResponseCodec();

    public ProtocolLobbyView(UUID sessionId, LobbyRoomListingAccess rooms, PacketOutput output, int maxFrameBytes) {
        this.sessionId = sessionId;
        this.rooms = rooms;
        this.output = output;
        roomCodec = new LobbyRoomListBatchCodec(maxFrameBytes);
    }

    @Override
    public CompletionStage<Void> initialize(int population, List<ChannelParticipant> users) {
        var snapshot = rooms.snapshot(sessionId);

        if (snapshot.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        var current = snapshot.orElseThrow();
        var batch = new ArrayList<byte[]>();
        batch.add(codec.enter(new PrLobbyEnter(population, 0)));
        batch.addAll(roomCodec.initialize(packs(current)));
        batch.add(codec.users(new PcLobbyInitUserList(map(users))));
        var delivery = output.send(batch);
        log(current, "INITIAL");
        return delivery;
    }

    @Override
    public CompletionStage<Void> update(List<ChannelParticipant> added, List<CharacterId> removed) {
        return output.send(
            List.of(
                codec.update(new PcLobbyUpdateUserList(map(added), removed.stream().map(CharacterId::value).toList()))
            )
        );
    }

    @Override
    public CompletionStage<Void> roomChanged(int roomId) {
        var snapshot = rooms.snapshot(sessionId);

        if (snapshot.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        var current = snapshot.orElseThrow();
        var changed = current.rooms().stream().filter(room -> room.id().value() == roomId)
            .map(room -> new LobbyRoomListMapper().map(room)).toList();

        if (changed.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        var delivery = output.send(roomCodec.update(changed));
        LOG.log(
            LobbyLogEvent.ROOM_LIST_QUEUED,
            recipientSessionId(sessionId),
            channelId(current.channelId()),
            roomId(roomId),
            roomCount(changed.size()),
            outcome("UPSERT")
        );
        return delivery;
    }

    @Override
    public CompletionStage<Void> roomsReplaced() {
        var snapshot = rooms.snapshot(sessionId);

        if (snapshot.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        var current = snapshot.orElseThrow();
        var delivery = output.send(roomCodec.initialize(packs(current)));
        log(current, "REPLACE");
        return delivery;
    }

    @Override
    public void close(String reason) {
        output.close(reason);
    }

    private List<GameSessionInfoPack> packs(LobbyRoomListSnapshot snapshot) {
        return snapshot.rooms().stream().map(room -> new LobbyRoomListMapper().map(room)).toList();
    }

    private void log(LobbyRoomListSnapshot snapshot, String kind) {
        LOG.log(
            LobbyLogEvent.ROOM_LIST_QUEUED,
            recipientSessionId(sessionId),
            channelId(snapshot.channelId()),
            roomCount(snapshot.rooms().size()),
            outcome(kind)
        );
    }

    @Override
    public CompletionStage<Void> unavailable() {
        return output.send(List.of(codec.enter(new PrLobbyEnter(0, 4))));
    }

    @Override
    public CompletionStage<Void> left() {
        return output.send(List.of(codec.leave(new PrLobbyLeave())));
    }

    private List<UserSimpleInfo> map(List<ChannelParticipant> users) {
        return users.stream().map(
            user -> new UserSimpleInfo(
                user.characterId().value(),
                user.name().value(),
                user.level(),
                switch (user.gender()) {
                    case MALE -> 1;
                    case FEMALE -> 2;
                }
            )
        ).toList();
    }
}
