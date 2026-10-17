package taan.online.room.view;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import taan.online.protocol.PacketOutput;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PcGameSessionUpdateOption;
import taan.online.room.protocol.packet.PcGameSessionUpdateSlot;
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
    public void close(String reason) {
        output.close(reason);
    }
}
