package taan.online.match.protocol.packet;

import java.util.List;

public record PqGameSessionReportEnemies(List<ReportedEnemyPair> enemies) {
    public static final int TYPE_ID = 0x89420a61;

    public PqGameSessionReportEnemies {
        enemies = List.copyOf(enemies);
    }
}
