package taan.online.match.repository.jdbc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

final class LostCommitReplyDataSource {
    private LostCommitReplyDataSource() {
    }

    static DataSource wrap(DataSource source) {
        var loseReply = new AtomicBoolean(true);
        var wrapped = Proxy.newProxyInstance(
            DataSource.class.getClassLoader(),
            new Class<?>[]{DataSource.class},
            (proxy, method, arguments) -> {
                Object returned = invoke(method, source, arguments);

                if (returned instanceof Connection connection) {
                    return connection(connection, loseReply);
                }

                return returned;
            }
        );
        return DataSource.class.cast(wrapped);
    }

    private static Connection connection(Connection connection, AtomicBoolean loseReply) {
        var wrapped = Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, method, arguments) -> {
                Object returned = invoke(method, connection, arguments);

                if (method.getName().equals("commit") && loseReply.compareAndSet(true, false)) {
                    throw new SQLException("Commit reply was lost after the server committed", "08006");
                }

                return returned;
            }
        );
        return Connection.class.cast(wrapped);
    }

    private static Object invoke(Method method, Object receiver, Object[] arguments) throws Throwable {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
