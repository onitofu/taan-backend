package taan.online.inventory.repository.jdbc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import javax.sql.DataSource;

final class InventoryJdbcFaults {
    private InventoryJdbcFaults() {
    }

    static DataSource source(DataSource actual, Set<String> failures, Integer genderOverride) {
        return wrap(DataSource.class, actual, "source", failures, genderOverride);
    }

    private static <T> T wrap(Class<T> type, T actual, String prefix, Set<String> failures, Integer genderOverride) {
        return type.cast(
            Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    String call = prefix + "." + method.getName();

                    if (failures.contains(call)) {
                        if (call.equals("connection.close")) {
                            ((Connection) actual).close();
                        }

                        throw new SQLException("PrivateInventoryJdbcValue", "08006");
                    }

                    if (genderOverride != null && call.equals("rows.getInt") && args[0].equals("gender")) {
                        return genderOverride;
                    }

                    Object result;

                    try {
                        result = method.invoke(actual, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }

                    if (result instanceof Connection connection) {
                        return wrap(Connection.class, connection, "connection", failures, genderOverride);
                    }

                    if (result instanceof PreparedStatement statement) {
                        return wrap(PreparedStatement.class, statement, "statement", failures, genderOverride);
                    }

                    if (result instanceof Statement statement) {
                        return wrap(Statement.class, statement, "statement", failures, genderOverride);
                    }

                    if (result instanceof ResultSet rows) {
                        return wrap(ResultSet.class, rows, "rows", failures, genderOverride);
                    }

                    return result;
                }
            )
        );
    }
}
