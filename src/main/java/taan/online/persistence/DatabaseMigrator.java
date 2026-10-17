package taan.online.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

public final class DatabaseMigrator {
    public void migrate(DataSource dataSource) {
        Flyway.configure().dataSource(dataSource).cleanDisabled(true).baselineOnMigrate(false).load().migrate();
    }
}
