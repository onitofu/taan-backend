package taan.online.account.repository.jdbc;

import java.sql.SQLException;

interface JdbcTestInvocation {
    Object invoke(String method, Object[] arguments) throws SQLException;
}
