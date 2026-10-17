package taan.online.account.repository.jdbc;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import taan.online.TestFixtures;

final class JdbcTestDatabase {
    private final List<String> calls = new ArrayList<>();
    private boolean hasRow = true;
    private String fail = "";
    private boolean failRollback;
    private short gender = 2;
    private short boundGender;

    DataSource source() {
        return stub(
            DataSource.class,
            (method, arguments) -> {
                called(method);

                if (method.equals("getConnection")) {
                    return connection();
                }

                throw new AssertionError(method);
            }
        );
    }

    private Connection connection() {
        return stub(
            Connection.class,
            (method, arguments) -> {
                called(method);

                if (method.equals("prepareStatement")) {
                    return statement();
                }

                return null;
            }
        );
    }

    private PreparedStatement statement() {
        return stub(
            PreparedStatement.class,
            (method, arguments) -> {
                called(method);

                if (method.equals("setShort")) {
                    boundGender = (Short) arguments[1];
                }

                if (method.equals("executeQuery")) {
                    return rows();
                }

                return null;
            }
        );
    }

    private ResultSet rows() {
        return stub(
            ResultSet.class,
            (method, arguments) -> {
                calls.add("rows." + method);
                return switch (method) {
                    case "next", "getBoolean" -> hasRow;
                    case "getLong" -> 1L;
                    case "getShort" -> gender;
                    case "getString" -> arguments[0].equals("login") ? TestFixtures.LOGIN : TestFixtures.VERIFIER;
                    case "getObject" -> OffsetDateTime.parse("2026-10-17T00:00:00Z");
                    case "close" -> null;
                    default -> throw new AssertionError(method);
                };
            }
        );
    }

    private void called(String method) throws SQLException {
        calls.add(method);

        if (method.equals(fail) || method.equals("rollback") && failRollback) {
            throw new SQLException("secret-value", "XX000");
        }
    }

    List<String> calls() {
        return List.copyOf(calls);
    }

    short boundGender() {
        return boundGender;
    }

    void hasRow(boolean hasRow) {
        this.hasRow = hasRow;
    }

    void fail(String fail) {
        this.fail = fail;
    }

    void failRollback(boolean failRollback) {
        this.failRollback = failRollback;
    }

    void gender(short gender) {
        this.gender = gender;
    }

    private static <T> T stub(Class<T> type, JdbcTestInvocation invocation) {
        return type.cast(
            Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> invocation.invoke(method.getName(), arguments)
            )
        );
    }
}
