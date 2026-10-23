package taan.online.room.view;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterId;
import taan.online.protocol.PacketOutput;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.protocol.connection.packet.PcNoticeMsg;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomTeam;
import taan.online.room.model.RoomTeamChange;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PcGameSessionCancelChief;
import taan.online.room.protocol.packet.PcGameSessionChallengeChief;
import taan.online.room.protocol.packet.PcGameSessionChangeGameOption;
import taan.online.room.protocol.packet.PcGameSessionChangeTeam;
import taan.online.room.protocol.packet.PcGameSessionChangeWeapon;
import taan.online.room.protocol.packet.PcGameSessionChiefDecision;
import taan.online.room.protocol.packet.PcGameSessionOpenCloseSlot;
import taan.online.room.protocol.packet.PcGameSessionReady;
import taan.online.room.protocol.packet.PcGameSessionUpdateOption;
import taan.online.room.protocol.packet.PcGameSessionUpdateSlot;
import taan.online.room.protocol.packet.PcGameSessionUserExpel;
import taan.online.room.service.RoomPresenceOutput;
import taan.online.room.service.model.RoomSnapshot;

public final class ProtocolRoomView implements RoomPresenceOutput {
    private final PacketOutput output;
    private final RoomSnapshotMapper mapper = new RoomSnapshotMapper();
    private final RoomResponseCodec codec = new RoomResponseCodec();

    public ProtocolRoomView(PacketOutput output) {
        this.output = output;
    }

    @Override
    public CompletionStage<Void> initialize(RoomSnapshot snapshot) {
        return output.send(List.of(codec.encode(mapper.map(snapshot))));
    }

    @Override
    public CompletionStage<Void> update(RoomSnapshot snapshot, List<Integer> changedSlots, boolean ownerChanged) {
        var batch = new ArrayList<byte[]>();

        for (int index : changedSlots) {
            batch.add(codec.encode(new PcGameSessionUpdateSlot(1, mapper.slot(snapshot.slots().get(index)), "")));
        }

        if (ownerChanged) {
            batch.add(codec.encode(new PcGameSessionUpdateOption(mapper.option(snapshot))));
        }

        return output.send(batch);
    }

    @Override
    public CompletionStage<Void> slotAvailability(int slotIndex, RoomSlotState state) {
        int selector = switch (state) {
            case OPEN -> 1;
            case CLOSED -> 0;
            case OCCUPIED, READY -> throw new IllegalArgumentException("Occupied slot is not an availability state");
        };
        return output.send(List.of(codec.encode(new PcGameSessionOpenCloseSlot(selector, slotIndex))));
    }

    @Override
    public CompletionStage<Void> teamChanged(RoomTeamChange change) {
        var source = change.source();
        var destination = change.destination();
        var response = new PcGameSessionChangeTeam(
            source.participant().character().accountId().value(),
            destination.team() == RoomTeam.BLUE ? 1 : 2,
            source.index(),
            255,
            destination.index(),
            255
        );
        return output.send(List.of(codec.encode(response)));
    }

    @Override
    public CompletionStage<Void> readyChanged(AccountId accountId, boolean ready) {
        return output.send(List.of(codec.encode(new PcGameSessionReady(accountId.value(), ready))));
    }

    @Override
    public CompletionStage<Void> weaponChanged(CharacterId characterId, int weaponId) {
        return output.send(List.of(codec.encode(new PcGameSessionChangeWeapon(characterId.value(), weaponId))));
    }

    @Override
    public CompletionStage<Void> settingsChanged(RoomSettings settings, boolean rejected) {
        var response = new PcGameSessionChangeGameOption(
            settings.mapId(),
            new RoomModeMapper().map(settings.mode()),
            new RoomRuleMapper().map(settings.rule()),
            255
        );
        var batch = new ArrayList<byte[]>();
        batch.add(codec.encode(response));

        if (rejected) {
            batch.add(new NoticeMessageCodec().encode(new PcNoticeMsg("Requested room settings are not allowed.")));
        }

        return output.send(batch);
    }

    @Override
    public CompletionStage<Void> expelled(AccountId targetAccountId) {
        return output.send(List.of(codec.encode(new PcGameSessionUserExpel("", "", targetAccountId.value()))));
    }

    @Override
    public CompletionStage<Void> captainSelectionOpened() {
        return output.send(List.of(codec.encode(new PcGameSessionChiefDecision())));
    }

    @Override
    public CompletionStage<Void> captainCandidateChanged(long characterId, boolean candidate) {
        byte[] message = candidate ? codec.encode(new PcGameSessionChallengeChief(characterId))
            : codec.encode(new PcGameSessionCancelChief(characterId));
        return output.send(List.of(message));
    }

    @Override
    public CompletionStage<Void> notice(String message) {
        return output.send(List.of(new NoticeMessageCodec().encode(new PcNoticeMsg(message))));
    }

    @Override
    public void close(String reason) {
        output.close(reason);
    }
}
