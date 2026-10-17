package taan.online.lobby.view;

import java.util.List;
import java.util.concurrent.CompletionStage;
import taan.online.channel.model.ChannelParticipant;
import taan.online.channel.service.ChannelPresenceOutput;
import taan.online.character.model.CharacterId;
import taan.online.lobby.protocol.codec.LobbyResponseCodec;
import taan.online.lobby.protocol.packet.PcLobbyInitRoomList;
import taan.online.lobby.protocol.packet.PcLobbyInitUserList;
import taan.online.lobby.protocol.packet.PcLobbyUpdateUserList;
import taan.online.lobby.protocol.packet.PrLobbyEnter;
import taan.online.lobby.protocol.packet.PrLobbyLeave;
import taan.online.lobby.protocol.packet.UserSimpleInfo;
import taan.online.protocol.PacketOutput;

public final class ProtocolLobbyView implements ChannelPresenceOutput {
    private final PacketOutput output;
    private final LobbyResponseCodec codec = new LobbyResponseCodec();

    public ProtocolLobbyView(PacketOutput output) {
        this.output = output;
    }

    @Override
    public CompletionStage<Void> initialize(int population, List<ChannelParticipant> users) {
        return output.send(
            List.of(
                codec.enter(new PrLobbyEnter(population, 0)),
                codec.rooms(new PcLobbyInitRoomList()),
                codec.users(new PcLobbyInitUserList(map(users)))
            )
        );
    }

    @Override
    public CompletionStage<Void> update(List<ChannelParticipant> added, List<CharacterId> removed) {
        return output.send(
            List.of(
                codec.update(
                    new PcLobbyUpdateUserList(
                        map(added),
                        removed.stream().map(
                            CharacterId::value
                        ).toList()
                    )
                )
            )
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
