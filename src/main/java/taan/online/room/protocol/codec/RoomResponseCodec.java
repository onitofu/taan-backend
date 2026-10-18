package taan.online.room.protocol.codec;

import java.util.ArrayList;
import java.util.List;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.GameSlotInfo;
import taan.online.room.protocol.packet.PcGameSessionCancelChief;
import taan.online.room.protocol.packet.PcGameSessionChallengeChief;
import taan.online.room.protocol.packet.PcGameSessionChangeGameOption;
import taan.online.room.protocol.packet.PcGameSessionChangeTeam;
import taan.online.room.protocol.packet.PcGameSessionChangeWeapon;
import taan.online.room.protocol.packet.PcGameSessionChiefDecision;
import taan.online.room.protocol.packet.PcGameSessionInit;
import taan.online.room.protocol.packet.PcGameSessionOpenCloseSlot;
import taan.online.room.protocol.packet.PcGameSessionReady;
import taan.online.room.protocol.packet.PcGameSessionUpdateOption;
import taan.online.room.protocol.packet.PcGameSessionUpdateSlot;
import taan.online.room.protocol.packet.PcGameSessionUserExpel;
import taan.online.room.protocol.packet.PrGameSessionCreateFail;
import taan.online.room.protocol.packet.PrGameSessionEnterFail;
import taan.online.room.protocol.packet.PrGameSessionLeave;
import taan.online.room.protocol.packet.PrGameSessionQuickJoinFail;

public final class RoomResponseCodec {
    public byte[] encode(PcGameSessionChiefDecision response) {
        return new BinaryWriter().unsignedInt(Integer.toUnsignedLong(PcGameSessionChiefDecision.TYPE_ID))
            .toByteArray();
    }

    public PcGameSessionChiefDecision chiefDecision(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionChiefDecision.TYPE_ID);
        var message = new PcGameSessionChiefDecision();
        input.finish();
        return message;
    }

    public byte[] encode(PcGameSessionChallengeChief response) {
        return new BinaryWriter().unsignedInt(Integer.toUnsignedLong(PcGameSessionChallengeChief.TYPE_ID))
            .unsignedInt(response.characterId())
            .toByteArray();
    }

    public PcGameSessionChallengeChief challengeChief(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionChallengeChief.TYPE_ID);
        var message = new PcGameSessionChallengeChief(input.unsignedInt());
        input.finish();
        return message;
    }

    public byte[] encode(PcGameSessionCancelChief response) {
        return new BinaryWriter().unsignedInt(Integer.toUnsignedLong(PcGameSessionCancelChief.TYPE_ID))
            .unsignedInt(response.characterId())
            .toByteArray();
    }

    public PcGameSessionCancelChief cancelChief(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionCancelChief.TYPE_ID);
        var message = new PcGameSessionCancelChief(input.unsignedInt());
        input.finish();
        return message;
    }

    public byte[] encode(PcGameSessionUserExpel response) {
        return new BinaryWriter().unsignedInt(PcGameSessionUserExpel.TYPE_ID).wideString(response.text0())
            .wideString(response.text1()).unsignedInt(response.targetAccountId()).toByteArray();
    }

    public PcGameSessionUserExpel expel(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionUserExpel.TYPE_ID);
        var response = new PcGameSessionUserExpel(input.wideString(), input.wideString(), input.unsignedInt());
        input.finish();
        return response;
    }

    public byte[] encode(PcGameSessionChangeGameOption response) {
        return new BinaryWriter().unsignedInt(Integer.toUnsignedLong(PcGameSessionChangeGameOption.TYPE_ID))
            .unsignedShort(response.mapId()).unsignedShort(response.gameType()).unsignedByte(response.rule())
            .unsignedByte(response.missionDifficultyOrIgnored()).toByteArray();
    }

    public PcGameSessionChangeGameOption settings(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionChangeGameOption.TYPE_ID);
        var response = new PcGameSessionChangeGameOption(
            input.unsignedShort(),
            input.unsignedShort(),
            input.unsignedByte(),
            input.unsignedByte()
        );
        input.finish();
        return response;
    }

    public byte[] encode(PcGameSessionChangeWeapon response) {
        validateWeaponIdentity(response.characterId());
        return new BinaryWriter().unsignedInt(Integer.toUnsignedLong(PcGameSessionChangeWeapon.TYPE_ID))
            .unsignedInt(response.characterId()).unsignedShort(response.weaponId()).toByteArray();
    }

    public PcGameSessionChangeWeapon changeWeapon(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionChangeWeapon.TYPE_ID);
        var response = new PcGameSessionChangeWeapon(input.unsignedInt(), input.unsignedShort());
        input.finish();
        validateWeaponIdentity(response.characterId());
        return response;
    }

    private void validateWeaponIdentity(long value) {
        if (value < 1 || value > 0xffff_ffffL) {
            throw new MalformedPacketException("INVALID_ROOM_WEAPON_CHARACTER");
        }
    }

    public byte[] encode(PcGameSessionReady response) {
        if (response.accountId() < 1 || response.accountId() > 0xffff_ffffL) {
            throw new MalformedPacketException("INVALID_ROOM_READY_ACCOUNT");
        }

        return new BinaryWriter().unsignedInt(PcGameSessionReady.TYPE_ID).unsignedInt(response.accountId())
            .unsignedByte(response.ready() ? 1 : 0).toByteArray();
    }

    public PcGameSessionReady ready(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionReady.TYPE_ID);
        long accountId = input.unsignedInt();
        int ready = input.unsignedByte();
        input.finish();

        if (accountId == 0 || ready > 1) {
            throw new MalformedPacketException("INVALID_ROOM_READY");
        }

        return new PcGameSessionReady(accountId, ready == 1);
    }

    public byte[] encode(PcGameSessionChangeTeam response) {
        validateTeamChange(response);
        return new BinaryWriter().unsignedInt(PcGameSessionChangeTeam.TYPE_ID).unsignedInt(response.accountId())
            .unsignedByte(response.team()).unsignedByte(response.oldSlot0()).unsignedByte(response.oldSlot1())
            .unsignedByte(response.newSlot0()).unsignedByte(response.newSlot1()).toByteArray();
    }

    public PcGameSessionChangeTeam changeTeam(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionChangeTeam.TYPE_ID);
        var response = new PcGameSessionChangeTeam(
            input.unsignedInt(),
            input.unsignedByte(),
            input.unsignedByte(),
            input.unsignedByte(),
            input.unsignedByte(),
            input.unsignedByte()
        );
        input.finish();
        validateTeamChange(response);
        return response;
    }

    private void validateTeamChange(PcGameSessionChangeTeam response) {
        if (response.accountId() < 1 || response.accountId() > 0xffff_ffffL
            || response.team() < 1 || response.team() > 2
            || response.oldSlot0() < 0 || response.oldSlot0() > 7
            || response.newSlot0() < 0 || response.newSlot0() > 7
            || response.oldSlot1() != 255 || response.newSlot1() != 255
            || ((response.newSlot0() & 2) == 0 ? 1 : 2) != response.team()
            || (response.oldSlot0() & 2) == (response.newSlot0() & 2)) {
            throw new MalformedPacketException("INVALID_ROOM_TEAM_CHANGE");
        }
    }

    public byte[] encode(PcGameSessionOpenCloseSlot response) {
        validateAvailability(response);
        return new BinaryWriter().unsignedInt(Integer.toUnsignedLong(PcGameSessionOpenCloseSlot.TYPE_ID))
            .unsignedByte(response.selector()).unsignedByte(response.slotIndex()).toByteArray();
    }

    public PcGameSessionOpenCloseSlot slotAvailability(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionOpenCloseSlot.TYPE_ID);
        var response = new PcGameSessionOpenCloseSlot(input.unsignedByte(), input.unsignedByte());
        input.finish();
        validateAvailability(response);
        return response;
    }

    private void validateAvailability(PcGameSessionOpenCloseSlot response) {
        if (response.selector() < 0 || response.selector() > 1
            || response.slotIndex() < 0 || response.slotIndex() > 7) {
            throw new MalformedPacketException("INVALID_ROOM_SLOT_AVAILABILITY");
        }
    }

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
