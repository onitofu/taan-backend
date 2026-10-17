package taan.online.protocol.connection.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import taan.online.protocol.connection.packet.PrFirstPacket;

public final class FirstPacketResponseCodec {
    public byte[] encode(Clock clock) {
        var now = clock.instant().atOffset(ZoneOffset.UTC);
        int days = Math.toIntExact(ChronoUnit.DAYS.between(LocalDate.of(1900, 1, 1), now.toLocalDate()));
        var packet = new PrFirstPacket(days, now.toLocalTime().toSecondOfDay() / 4);
        return ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).putInt(PrFirstPacket.TYPE_ID)
            .put((byte) 0).putShort((short) packet.days()).putShort((short) packet.ticks()).put((byte) 0).array();
    }
}
