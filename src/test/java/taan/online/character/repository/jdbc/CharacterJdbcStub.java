package taan.online.character.repository.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

final class CharacterJdbcStub {
    final Set<String> failures = new HashSet<>();
    final List<String> calls = new ArrayList<>();
    boolean ownerExists = true;
    boolean incompleteInventories;
    boolean corruptInventory;
    boolean nameConflict;
    boolean nullSlot;
    boolean missingEquipment;
    short gender = 2;

    DataSource source() {
        return proxy(
            DataSource.class,
            (instance, method, arguments) -> {
                called("source." + method.getName());
                return connection();
            }
        );
    }

    private Connection connection() {
        return proxy(
            Connection.class,
            (instance, method, arguments) -> {
                called("connection." + method.getName());

                if (method.getName().equals("prepareStatement")) {
                    return statement((String) arguments[0]);
                }

                return null;
            }
        );
    }

    private PreparedStatement statement(String sql) {
        return proxy(
            PreparedStatement.class,
            (instance, method, arguments) -> {
                called("statement." + method.getName());

                if (method.getName().equals("executeQuery")) {
                    return rows(sql);
                }

                if (method.getName().equals("executeUpdate")) {
                    return 1;
                }

                if (method.getName().equals("executeBatch")) {
                    return new int[16];
                }

                return null;
            }
        );
    }

    private ResultSet rows(String sql) {
        int count = 1;

        if (sql.contains("SELECT i.id")) {
            count = incompleteInventories ? 2 : 3;
        } else if (sql.startsWith("SELECT c.id")) {
            count = 16;
        } else if (sql.startsWith("SELECT gender") && !ownerExists
            || sql.startsWith("INSERT") && nameConflict) {
            count = 0;
        }

        int rowCount = count;
        var index = new AtomicInteger();
        return proxy(
            ResultSet.class,
            (instance, method, arguments) -> {
                called("rows." + method.getName());
                return switch (method.getName()) {
                    case "next" -> index.incrementAndGet() <= rowCount;
                    case "getBoolean" -> false;
                    case "getShort" -> gender;
                    case "getLong" -> arguments[0].equals("item_id") && !sql.contains("FROM inventory_items") ? 0L : 1L;
                    case "getInt" -> switch (arguments[0].toString()) {
                        case "slot" -> index.get() - 1;
                        case "level", "quantity" -> 1;
                        default -> 0;
                    };
                    case "getString" -> arguments[0].equals("kind")
                        ? corruptInventory ? "INVALID" : List.of("BAG", "EQUIPMENT", "JEWEL_BOX").get(index.get() - 1)
                        : "Luma";
                    case "getObject" -> arguments[0].equals("equipment_id") ? missingEquipment ? null : 1L
                        : arguments[0].equals("slot")
                        ? nullSlot ? null : index.get() - 1 : OffsetDateTime.parse("2026-10-17T00:00:00Z");
                    case "close" -> null;
                    default -> throw new AssertionError(method.getName());
                };
            }
        );
    }

    private void called(String stage) throws SQLException {
        calls.add(stage);

        if (failures.contains(stage)) {
            throw new SQLException("Luma private-database-value", "XX000");
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
