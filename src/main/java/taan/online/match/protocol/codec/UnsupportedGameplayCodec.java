package taan.online.match.protocol.codec;

import static taan.online.match.protocol.packet.UnsupportedGameplayPacket.MOVE_TYPE_ID;
import static taan.online.match.protocol.packet.UnsupportedGameplayPacket.PASS_TURN_TYPE_ID;
import static taan.online.match.protocol.packet.UnsupportedGameplayPacket.SCENARIO_TYPE_ID;
import static taan.online.match.protocol.packet.UnsupportedGameplayPacket.USE_ITEM_TYPE_ID;

import taan.online.match.protocol.packet.UnsupportedGameplayPacket;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.MalformedPacketException;

public final class UnsupportedGameplayCodec {
    public UnsupportedGameplayPacket decode(int type, byte[] root) {
        var input = new BinaryReader(root, type);
        int expected = switch (type) {
            case MOVE_TYPE_ID -> 10;
            case PASS_TURN_TYPE_ID -> 14;
            case USE_ITEM_TYPE_ID -> 7;
            case SCENARIO_TYPE_ID -> -1;
            default -> throw new MalformedPacketException("UNSUPPORTED_MATCH_PACKET");
        };

        if (expected >= 0 && input.remaining() != expected) {
            throw new MalformedPacketException("INVALID_PREVIEW_ACTION_SIZE");
        }

        return new UnsupportedGameplayPacket(type);
    }

    public static boolean accepts(int type) {
        return type == MOVE_TYPE_ID || type == PASS_TURN_TYPE_ID
            || type == USE_ITEM_TYPE_ID || type == SCENARIO_TYPE_ID;
    }
}
