package taan.online.room.protocol.codec;

import java.util.ArrayList;
import java.util.List;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.GameSlotInfo;
import taan.online.room.protocol.packet.PcGameSessionInit;
import taan.online.room.protocol.packet.PcGameSessionUpdateOption;
import taan.online.room.protocol.packet.PcGameSessionUpdateSlot;
import taan.online.room.protocol.packet.PrGameSessionCreateFail;
import taan.online.room.protocol.packet.PrGameSessionEnterFail;
import taan.online.room.protocol.packet.PrGameSessionLeave;
import taan.online.room.protocol.packet.PrGameSessionQuickJoinFail;

public final class RoomResponseCodec {
    public byte[] encode(PrGameSessionQuickJoinFail response) {
        return new BinaryWriter().unsignedInt(Integer.toUnsignedLong(PrGameSessionQuickJoinFail.TYPE_ID)).toByteArray();
    }

    public PrGameSessionQuickJoinFail quickJoinFail(byte[] root) {
        var input = new BinaryReader(root, PrGameSessionQuickJoinFail.TYPE_ID);
        input.finish();
        return new PrGameSessionQuickJoinFail();
    }

    public byte[] encode(PrGameSessionCreateFail response) {
        return new BinaryWriter().unsignedInt(
            PrGameSessionCreateFail.TYPE_ID
        ).unsignedByte(
            response.reason()
        ).toByteArray();
    }

    public PrGameSessionCreateFail createFail(byte[] root) {
        var input = new BinaryReader(root, PrGameSessionCreateFail.TYPE_ID);
        var response = new PrGameSessionCreateFail(input.unsignedByte());
        input.finish();
        return response;
    }

    public byte[] encode(PrGameSessionEnterFail response) {
        return new BinaryWriter().unsignedInt(
            PrGameSessionEnterFail.TYPE_ID
        ).unsignedByte(
            response.reason()
        ).toByteArray();
    }

    public PrGameSessionEnterFail enterFail(byte[] root) {
        var input = new BinaryReader(root, PrGameSessionEnterFail.TYPE_ID);
        var response = new PrGameSessionEnterFail(input.unsignedByte());
        input.finish();
        return response;
    }

    public byte[] encode(PcGameSessionInit response) {
        validateSlots(response.slots());
        var output = new BinaryWriter().unsignedInt(PcGameSessionInit.TYPE_ID);
        new GameSessionInfoCodec().encode(output, response.info());
        new GameSessionOptionCodec().encode(output, response.option());
        output.unsignedInt(response.slots().size());
        response.slots().forEach(slot -> new GameSlotInfoCodec().encode(output, slot));
        return output.unsignedByte(response.missionDifficulty()).wideString(response.sideName1())
            .wideString(response.sideName2()).toByteArray();
    }

    public PcGameSessionInit initialize(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionInit.TYPE_ID);
        var info = new GameSessionInfoCodec().decode(input);
        var option = new GameSessionOptionCodec().decode(input);

        if (input.unsignedInt() != 8) {
            throw new MalformedPacketException("INVALID_ROOM_SLOT_COUNT");
        }

        var slots = new ArrayList<GameSlotInfo>();

        for (int index = 0; index < 8; index++) {
            slots.add(new GameSlotInfoCodec().decode(input));
        }

        validateSlots(slots);
        var response = new PcGameSessionInit(
            info,
            option,
            slots,
            input.unsignedByte(),
            input.wideString(),
            input.wideString()
        );
        input.finish();
        return response;
    }

    public byte[] encode(PcGameSessionUpdateSlot response) {
        var output = new BinaryWriter().unsignedInt(
            PcGameSessionUpdateSlot.TYPE_ID
        ).unsignedByte(
            response.resetWidget()
        );
        new GameSlotInfoCodec().encode(output, response.slot());
        return output.wideString(response.text()).toByteArray();
    }

    public PcGameSessionUpdateSlot slot(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionUpdateSlot.TYPE_ID);
        var response = new PcGameSessionUpdateSlot(
            input.unsignedByte(),
            new GameSlotInfoCodec().decode(input),
            input.wideString()
        );
        input.finish();
        return response;
    }

    public byte[] encode(PcGameSessionUpdateOption response) {
        var output = new BinaryWriter().unsignedInt(Integer.toUnsignedLong(PcGameSessionUpdateOption.TYPE_ID));
        new GameSessionOptionCodec().encode(output, response.option());
        return output.toByteArray();
    }

    public PcGameSessionUpdateOption option(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionUpdateOption.TYPE_ID);
        var response = new PcGameSessionUpdateOption(new GameSessionOptionCodec().decode(input));
        input.finish();
        return response;
    }

    public byte[] encode(PrGameSessionLeave response) {
        return new BinaryWriter().unsignedInt(
            PrGameSessionLeave.TYPE_ID
        ).unsignedByte(
            response.success()
        ).toByteArray();
    }

    public PrGameSessionLeave leave(byte[] root) {
        var input = new BinaryReader(root, PrGameSessionLeave.TYPE_ID);
        var response = new PrGameSessionLeave(input.unsignedByte());
        input.finish();
        return response;
    }

    private void validateSlots(List<GameSlotInfo> slots) {
        if (slots.size() != 8) {
            throw new MalformedPacketException("INVALID_ROOM_SLOT_COUNT");
        }

        for (int index = 0; index < 8; index++) {
            if (slots.get(index).slotIndex() != index) {
                throw new MalformedPacketException("INVALID_ROOM_SLOT_ORDER");
            }
        }
    }
}
