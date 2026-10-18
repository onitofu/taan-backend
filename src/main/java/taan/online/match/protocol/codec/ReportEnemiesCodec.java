package taan.online.match.protocol.codec;

import java.util.ArrayList;
import taan.online.match.protocol.packet.PqGameSessionReportEnemies;
import taan.online.match.protocol.packet.ReportedEnemyPair;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class ReportEnemiesCodec {
    public byte[] encode(PqGameSessionReportEnemies message) {
        if (message.enemies().size() > 8) {
            throw new MalformedPacketException("INVALID_ENEMY_REPORT_COUNT");
        }

        var output = new BinaryWriter().unsignedInt(Integer.toUnsignedLong(PqGameSessionReportEnemies.TYPE_ID))
            .unsignedInt(message.enemies().size());

        for (var enemy : message.enemies()) {
            output.unsignedInt(enemy.opaqueA()).unsignedInt(enemy.opaqueB());
        }

        return output.toByteArray();
    }

    public PqGameSessionReportEnemies decode(byte[] root) {
        var input = new BinaryReader(root, PqGameSessionReportEnemies.TYPE_ID);
        int count = MatchWireChecks.count(input.unsignedInt(), 8, input.remaining(), 8);
        var enemies = new ArrayList<ReportedEnemyPair>();

        for (int index = 0; index < count; index++) {
            enemies.add(new ReportedEnemyPair(input.unsignedInt(), input.unsignedInt()));
        }

        input.finish();
        return new PqGameSessionReportEnemies(enemies);
    }
}
