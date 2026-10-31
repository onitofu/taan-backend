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
    final Map<String, ArrayDeque<SQLException>> failures = new HashMap<>();
    final MatchResult stored;
    boolean created = true;
    boolean missingCharacter;
    long experience = 900;
    long gold = 0;
    final Map<Long, Long> goldWrites = new HashMap<>();
    final List<Long> lockedCharacters = new ArrayList<>();
    final Map<Long, List<Long>> progressWrites = new HashMap<>();
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
        String kind = sql.startsWith("SELECT experience") ? "locked"
            : sql.startsWith("UPDATE characters SET experience") ? "progress"
            : sql.startsWith("INSERT INTO match_results") ? "result"
            : sql.contains("INSERT INTO match_participant_results") ? "participants"
            : sql.contains("SELECT finished_at") ? "read" : "readParticipants";
        var parameters = new HashMap<Integer, Object>();
        return proxy(
            PreparedStatement.class,
            (instance, method, arguments) -> {
                called(kind + "." + method.getName());

                if (method.getName().startsWith("set") && arguments.length >= 2) {
                    parameters.put((Integer) arguments[0], arguments[1]);
                }

                if (method.getName().equals("executeQuery") && kind.equals("locked")) {
                    lockedCharacters.add((Long) parameters.get(1));
                }

                if (method.getName().equals("executeUpdate") && kind.equals("progress")) {
                    goldWrites.put((Long) parameters.get(6), (Long) parameters.get(5));
                    progressWrites.put(
                        (Long) parameters.get(6),
                        List.of(
                            (Long) parameters.get(1),
                            ((Integer) parameters.get(2)).longValue(),
                            (Long) parameters.get(3),
                            (Long) parameters.get(4)
                        )
                    );
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
        int count = participants ? stored.participants().size() : kind.equals("locked")
            ? missingCharacter ? 0 : 1 : missingResult ? 0 : 1;
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

                if (kind.equals("locked")) {
                    return switch (column) {
                        case "experience" -> experience;
                        case "level" -> 1;
                        case "gold" -> gold;
                        case "force_medals", "kill_medals", "supply_medals" -> 0L;
                        default -> throw new AssertionError(column);
                    };
                }

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
                        case "kills" -> participant.kills();
                        case "kill_medal" -> participant.killMedal();
                        case "experience_before" -> participant.experienceReward() == null
                            ? null : participant.experienceReward().experienceBefore();
                        case "level_before" -> participant.experienceReward().levelBefore();
                        case "experience_after" -> participant.experienceReward().after().progress().experience();
                        case "level_after" -> participant.experienceReward().after().level();
                        case "experience_base_delta" -> participant.experienceReward().baseDelta();
                        case "experience_pcroom_delta" -> participant.experienceReward().pcroomDelta();
                        case "experience_medal_delta" -> participant.experienceReward().medalDelta();
                        case "experience_card_delta" -> participant.experienceReward() == null
                            ? 0L : participant.experienceReward().cardDelta();
                        case "gold_before" -> participant.goldReward() == null
                            ? null : participant.goldReward().before();
                        case "gold_after" -> participant.goldReward().after();
                        case "gold_base_delta" -> participant.goldReward().baseDelta();
                        case "gold_medal_delta" -> participant.goldReward().medalDelta();
                        case "gold_card_delta" -> participant.goldReward().cardDelta();
                        case "card_draw" -> participant.card() == null ? null : participant.card().draw();
                        case "card_kind" -> participant.card().kind().name();
                        case "card_amount" -> participant.card().amount();
                        case "accepted_shots" -> participant.shots() == null
                            ? null : participant.shots().acceptedShots();
                        case "damaging_shots" -> participant.shots().damagingShots();
                        case "experience_guard_adjustment" -> participant.experienceReward().guardAdjustment();
                        case "force_medals_after" -> participant.experienceReward().after().progress().forceMedals();
                        case "kill_medals_after" -> participant.experienceReward().after().progress().killMedals();
                        case "supply_medals_after" -> participant.experienceReward().after().progress().supplyMedals();
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
                    case "reward_duration_millis" -> stored.rewardDurationMillis();
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
