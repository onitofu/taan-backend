package taan.online.session.protocol.packet;

import java.util.List;

public record PcLogging(long characterId, List<ClientLogRecord> records) {
    public static final int TYPE_ID = 0x10b3037a;

    public PcLogging {
        records = List.copyOf(records);
    }
}
