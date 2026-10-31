package taan.online.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

public final class DatabasePool {
    private DatabasePool() {
    }

    public static HikariDataSource create(DataSource source, int size, int acquireTimeoutMillis) {
        var config = new HikariConfig();
        config.setDataSource(source);
        config.setPoolName("taan-database");
        config.setMaximumPoolSize(size);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(acquireTimeoutMillis);
        config.setValidationTimeout(Math.min(500, acquireTimeoutMillis - 1));
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }
}
