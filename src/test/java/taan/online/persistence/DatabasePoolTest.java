package taan.online.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import taan.online.CharacterTestDatabase;
import taan.online.LogCapture;
import taan.online.TestFixtures;

class DatabasePoolTest {
    @Test
    void boundsConnectionsTimesOutWaitersAndRollsBackBeforeReusingAConnection() throws Exception {
        try (
            var database = new CharacterTestDatabase();
            var logs = new LogCapture();
            var pool = DatabasePool.create(database.source(), 2, 500)
        ) {
            database.execute("CREATE TABLE pool_probe (value integer)");

            try (var first = pool.getConnection(); var second = pool.getConnection()) {
                assertEquals(2, pool.getHikariPoolMXBean().getActiveConnections());
                assertThrows(SQLException.class, pool::getConnection);
                first.setAutoCommit(false);

                try (var insert = first.prepareStatement("INSERT INTO pool_probe VALUES (1)")) {
                    insert.executeUpdate();
                }
                assertTrue(second.getAutoCommit());
            }

            try (
                var fresh = pool.getConnection();
                var read = fresh.prepareStatement("SELECT count(*) FROM pool_probe")
            ) {
                assertTrue(fresh.getAutoCommit());

                try (var rows = read.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                }
            }

            assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(database.source().getPassword()));
        }
    }
}
