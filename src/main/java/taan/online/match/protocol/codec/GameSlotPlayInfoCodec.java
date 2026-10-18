package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.GameSlotPlayInfo;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class GameSlotPlayInfoCodec {
    public GameSlotPlayInfo decode(BinaryReader input) {
        if (input.unsignedInt() != GameSlotPlayInfo.TYPE_ID) {
            throw new MalformedPacketException("UNEXPECTED_MATCH_PLAYER_TYPE");
        }

        var result = new GameSlotPlayInfo(
            input.unsignedByte(),
            input.signedInt(),
            input.signedInt(),
            input.unsignedByte(),
            input.signedInt(),
            input.signedInt(),
            input.unsignedInt(),
            input.unsignedShort(),
            input.unsignedShort(),
            input.unsignedShort(),
            input.unsignedShort()
        );
        validate(result);
        return result;
    }

    public void encode(BinaryWriter output, GameSlotPlayInfo player) {
        validate(player);
        output.unsignedInt(GameSlotPlayInfo.TYPE_ID).unsignedByte(player.slotIndex())
            .signedInt(player.x()).signedInt(player.y()).unsignedByte(player.facing())
            .signedInt(player.hp()).signedInt(player.maxHp()).unsignedInt(player.scalar34())
            .unsignedShort(player.weaponId()).unsignedShort(player.maxEnergy())
            .unsignedShort(player.scalar3c()).unsignedShort(player.initialEnergyGrant());
    }

    private void validate(GameSlotPlayInfo player) {
        MatchWireChecks.slot(player.slotIndex());
        MatchWireChecks.facing(player.facing());
    }
}
