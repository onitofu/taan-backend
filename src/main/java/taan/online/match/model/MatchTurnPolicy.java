package taan.online.match.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import taan.online.room.model.RoomTeam;
import taan.online.room.model.RoomRule;

public final class MatchTurnPolicy {
    private static final long MASK = 0xffffffffffffL;

    static MatchTurn roster(Match match, int firstTurnSlot) {
        int channelId = match.channelId();
        var participants = match.participants();
        var mode = match.mode();
        var rule = match.rule();
        var ownerSessionId = match.ownerSessionId();
        var captains = match.captains();
        var definitions = match.definitions();

        if (channelId < 1 || channelId > 65535 || participants.size() < 2 || participants.size() > 8
            || mode.usesItems() != (rule == RoomRule.BONUS)) {
            throw new IllegalArgumentException("Invalid match setup");
        }

        var sessions = new HashSet<UUID>();
        var admissions = new HashSet<UUID>();
        var accounts = new HashSet<Long>();
        var characters = new HashSet<Long>();
        var slots = new HashSet<Integer>();
        var spawns = new HashSet<Integer>();
        var teams = new HashSet<RoomTeam>();

        for (var participant : participants) {
            if (!sessions.add(participant.sessionId()) || !admissions.add(participant.admissionId())
                || !accounts.add(participant.character().accountId().value())
                || !characters.add(participant.character().id().value()) || !slots.add(participant.slotIndex())
                || !spawns.add(participant.spawn().index())
                || !definitions.map().spawns().contains(participant.spawn())) {
                throw new IllegalArgumentException("Invalid match roster");
            }

            teams.add(participant.team());
        }

        if (!slots.contains(firstTurnSlot) || !sessions.contains(ownerSessionId) || teams.size() != 2
            || mode.requiresCaptainSelection() != (captains != null)) {
            throw new IllegalArgumentException("Invalid match ownership or teams");
        }

        if (captains != null
            && (!slots.contains(captains.blueLeaderSlot()) || !slots.contains(captains.redLeaderSlot()))) {
            throw new IllegalArgumentException("Invalid match captains");
        }

        var order = slots.stream().sorted().toList();
        int first = order.indexOf(firstTurnSlot);
        var rotated = new ArrayList<>(order.subList(first, order.size()));
        rotated.addAll(order.subList(0, first));
        return new MatchTurn(firstTurnSlot, 0, 0, 0, 0, rotated, 1, OptionalLong.empty());
    }

    public MatchTurn initial(List<MatchParticipant> participants, long seed, int lootPickerScalar) {
        var blue = new ArrayList<>(participants.stream().filter(p -> p.team() == RoomTeam.BLUE)
            .map(MatchParticipant::slotIndex).toList());
        var red = new ArrayList<>(participants.stream().filter(p -> p.team() == RoomTeam.RED)
            .map(MatchParticipant::slotIndex).toList());
        seed = shuffle(blue, seed);
        seed = shuffle(red, seed);
        var first = bounded(seed, 2);
        var left = first.value() == 0 ? blue : red;
        var right = first.value() == 0 ? red : blue;
        var order = new ArrayList<Integer>();

        for (int index = 0; index < Math.max(left.size(), right.size()); index++) {
            if (index < left.size()) {
                order.add(left.get(index));
            }

            if (index < right.size()) {
                order.add(right.get(index));
            }
        }

        var wind = bounded(first.state(), 101);
        return new MatchTurn(
            order.getFirst(),
            wind.value() - 50,
            0,
            0,
            lootPickerScalar,
            order,
            1,
            OptionalLong.empty(),
            1,
            new HashSet<>(order),
            wind.state()
        );
    }

    public MatchTurn next(MatchTurn previous, Set<Integer> alive, long issuedAtNanos) {
        var remaining = new HashSet<>(previous.remainingThisRound());
        remaining.remove(previous.activeSlot());
        remaining.retainAll(alive);
        int wind = previous.windRaw();
        long seed = previous.randomState();
        long round = previous.round();

        if (remaining.isEmpty()) {
            int lower = Math.max(-50, wind - 20);
            var draw = bounded(seed, Math.min(50, wind + 20) - lower + 1);
            wind = lower + draw.value();
            seed = draw.state();
            round = Math.incrementExact(round);
            remaining.addAll(alive);
        }

        var order = previous.order();
        int at = order.indexOf(previous.activeSlot());

        for (int offset = 1; offset <= order.size(); offset++) {
            int slot = order.get((at + offset) % order.size());

            if (alive.contains(slot)) {
                return new MatchTurn(
                    slot,
                    wind,
                    round > 1 ? 10 : 0,
                    0,
                    previous.pickerBase(),
                    order,
                    Math.incrementExact(previous.revision()),
                    OptionalLong.of(issuedAtNanos),
                    round,
                    remaining,
                    seed
                );
            }
        }

        throw new IllegalStateException("No living turn participant remains");
    }

    private long shuffle(List<Integer> slots, long seed) {
        for (int index = slots.size() - 1; index > 0; index--) {
            var draw = bounded(seed, index + 1);
            seed = draw.state();
            Collections.swap(slots, index, draw.value());
        }

        return seed;
    }

    MatchRandomDraw bounded(long seed, int bound) {
        while (true) {
            seed = (seed * 0x5deece66dL + 11) & MASK;
            int bits = (int) (seed >>> 17);
            int value = bits % bound;

            if ((long) bits - value + bound - 1 <= Integer.MAX_VALUE) {
                return new MatchRandomDraw(value, seed);
            }
        }
    }
}
