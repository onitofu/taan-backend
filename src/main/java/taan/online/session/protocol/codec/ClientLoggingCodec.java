package taan.online.session.protocol.codec;

import java.util.ArrayList;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.MalformedPacketException;
import taan.online.session.protocol.packet.ClientLogRecord;
import taan.online.session.protocol.packet.PcLogging;

public final class ClientLoggingCodec {
    public PcLogging decode(byte[] root) {
        var input = new BinaryReader(root, PcLogging.TYPE_ID);
        long character = input.unsignedInt();
        long count = input.unsignedInt();

        if (count > input.remaining() / 19) {
            throw new MalformedPacketException("INVALID_CLIENT_LOG_COUNT");
        }

        var records = new ArrayList<ClientLogRecord>();

        for (int index = 0; index < count; index++) {
            records.add(
                new ClientLogRecord(
                    input.unsignedByte(),
                    input.unsignedByte(),
                    input.unsignedByte(),
                    input.unsignedInt(),
                    input.unsignedInt(),
                    input.unsignedInt(),
                    input.unsignedInt()
                )
            );
        }

        input.finish();
        return new PcLogging(character, records);
    }
}
