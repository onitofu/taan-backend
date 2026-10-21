package taan.online.match.repository.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import taan.online.match.model.MatchResult;
import taan.online.room.model.RoomTeam;

final class MatchJdbcStub {
    final List<String> calls = new ArrayList<>();
    final List<Long> recipients = new ArrayList<>();
    final Map<String, ArrayDeque<SQLException>> failures = new HashMap<>();
    final MatchResult stored;
    boolean created = true;
    boolean missingResult;
    boolean counterOverflow;
    boolean corruptCause;
    Short storedTeam;

    MatchJdbcStub(MatchResult stored) {
        this.stored = stored;
    }

    void fail(String stage, SQLException failure) {
        failures.computeIfAbsent(stage, ignored -> new ArrayDeque<>()).addLast(failure);
    }

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
                return method.getName().equals("prepareStatement") ? statement((String) arguments[0]) : null;
            }
        );
    }

    private PreparedStatement statement(String sql) {
        String kind = sql.startsWith("INSERT INTO match_results") ? "result"
            : sql.contains("INSERT INTO match_participant_results") ? "participants"
            : sql.startsWith("UPDATE") ? "medals" : sql.contains("SELECT finished_at") ? "read" : "readParticipants";
        return proxy(
            PreparedStatement.class,
            (instance, method, arguments) -> {
                called(kind + "." + method.getName());

                if (method.getName().equals("setLong") && kind.equals("medals")) {
                    recipients.add((Long) arguments[1]);
                }

                return switch (method.getName()) {
                    case "executeUpdate" -> kind.equals("result") ? created ? 1 : 0 : counterOverflow ? 0 : 1;
                    case "executeBatch" -> new int[stored.participants().size()];
                    case "executeQuery" -> rows(kind);
                    default -> null;
                };
            }
        );
    }

    private ResultSet rows(String kind) {
        var index = new AtomicInteger(-1);
        boolean participants = kind.equals("readParticipants");
        int count = participants ? stored.participants().size() : missingResult ? 0 : 1;
        return proxy(
            ResultSet.class,
            (instance, method, arguments) -> {
                called(kind + ".rows." + method.getName());

                if (method.getName().equals("next")) {
                    return index.incrementAndGet() < count;
                }

                if (method.getName().equals("close")) {
                    return null;
                }

                String column = arguments[0].toString();

                if (participants) {
                    var participant = stored.participants().get(index.get());
                    return switch (column) {
                        case "character_id" -> participant.characterId().value();
                        case "slot_index" -> participant.slotIndex();
                        case "team" -> storedTeam == null
                            ? (short) (participant.team() == RoomTeam.BLUE ? 1 : 2) : storedTeam;
                        case "damage" -> participant.damage();
                        case "award_eligible" -> participant.awardEligible();
                        case "force_medal" -> participant.forceMedal();
                        default -> throw new AssertionError(column);
                    };
                }

                return switch (column) {
                    case "finished_at" -> stored.finishedAt().atOffset(ZoneOffset.UTC);
                    case "winning_team" -> stored.winningTeam().isEmpty() ? null
                        : storedTeam == null ? (short) (stored.winningTeam().orElseThrow() == RoomTeam.BLUE ? 1 : 2)
                        : storedTeam;
                    case "cause" -> corruptCause ? "CORRUPT" : stored.cause().name();
                    case "policy_version" -> stored.policyVersion();
                    default -> throw new AssertionError(column);
                };
            }
        );
    }

    private void called(String stage) throws SQLException {
        calls.add(stage);
        var pending = failures.get(stage);

        if (pending != null && !pending.isEmpty()) {
            throw pending.removeFirst();
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
