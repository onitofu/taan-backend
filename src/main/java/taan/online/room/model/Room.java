package taan.online.room.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

public final class Room {
    private final RoomId id;
    private final UUID generation;
    private final int channelId;
    private final RoomSettings settings;
    private final List<RoomSlot> slots;
    private UUID ownerSessionId;
    private RoomState state = RoomState.WAITING;

    public Room(RoomId id, UUID generation, int channelId, RoomSettings settings, RoomParticipant owner) {
        this(
            id,
            generation,
            channelId,
            settings,
            owner,
            IntStream.range(0, 8).mapToObj(index -> new RoomSlot(index, RoomSlotState.OPEN, null)).toList()
        );
    }

    Room(
        RoomId id,
        UUID generation,
        int channelId,
        RoomSettings settings,
        RoomParticipant owner,
        List<RoomSlot> slots
    ) {
        this.id = Objects.requireNonNull(id);
        this.generation = Objects.requireNonNull(generation);
        this.settings = Objects.requireNonNull(settings);
        ownerSessionId = owner.sessionId();

        if (channelId < 1 || channelId > 65535 || slots.size() != 8) {
            throw new IllegalArgumentException("Invalid room layout");
        }

        for (int index = 0; index < 8; index++) {
            if (slots.get(index).index() != index || slots.get(index).participant() != null) {
                throw new IllegalArgumentException("Initial room slots must be ordered and empty");
            }
        }

        this.channelId = channelId;
        this.slots = new ArrayList<>(slots);
        this.slots.set(0, new RoomSlot(0, RoomSlotState.OCCUPIED, owner));
    }

    public RoomId id() {
        return id;
    }

    public UUID generation() {
        return generation;
    }

    public int channelId() {
        return channelId;
    }

    public RoomSettings settings() {
        return settings;
    }

    public UUID ownerSessionId() {
        return ownerSessionId;
    }

    public RoomParticipant owner() {
        return slots.stream().map(RoomSlot::participant).filter(Objects::nonNull)
            .filter(value -> value.sessionId().equals(ownerSessionId)).findFirst().orElseThrow();
    }

    public List<RoomSlot> slots() {
        return List.copyOf(slots);
    }

    public int playerCount() {
        return (int) slots.stream().filter(slot -> slot.state() == RoomSlotState.OCCUPIED).count();
    }

    public boolean contains(RoomParticipant participant) {
        return slots.stream().map(RoomSlot::participant).filter(Objects::nonNull).anyMatch(
            value -> value.sessionId().equals(participant.sessionId())
                || value.character().id().equals(participant.character().id())
                || value.character().accountId().equals(participant.character().accountId())
        );
    }

    public Optional<RoomSlot> enter(RoomParticipant participant) {
        if (state != RoomState.WAITING || playerCount() >= settings.capacity()) {
            return Optional.empty();
        }

        if (contains(participant)) {
            throw new IllegalArgumentException("Duplicate room participant");
        }

        long blue = slots.stream().filter(slot -> slot.state() == RoomSlotState.OCCUPIED)
            .filter(slot -> slot.team() == RoomTeam.BLUE).count();
        RoomTeam preferred = blue <= playerCount() - blue ? RoomTeam.BLUE : RoomTeam.RED;
        var target = slots.stream().filter(slot -> slot.state() == RoomSlotState.OPEN)
            .min(
                Comparator.comparingInt((RoomSlot slot) -> slot.team() == preferred ? 0 : 1)
                    .thenComparingInt(RoomSlot::index)
            );

        return target.map(
            slot -> {
                var occupied = new RoomSlot(slot.index(), RoomSlotState.OCCUPIED, participant);
                slots.set(slot.index(), occupied);
                return occupied;
            }
        );
    }

    public Optional<RoomSlot> leave(UUID sessionId) {
        var previous = slots.stream().filter(slot -> slot.participant() != null)
            .filter(slot -> slot.participant().sessionId().equals(sessionId)).findFirst();

        if (previous.isEmpty()) {
            return Optional.empty();
        }

        var removed = previous.orElseThrow();
        slots.set(removed.index(), new RoomSlot(removed.index(), RoomSlotState.OPEN, null));

        if (playerCount() == 0) {
            close();
        } else if (sessionId.equals(ownerSessionId)) {
            ownerSessionId = slots.stream().map(RoomSlot::participant).filter(Objects::nonNull)
                .findFirst().orElseThrow().sessionId();
        }

        return Optional.of(removed);
    }

    public RoomState state() {
        return state;
    }

    public boolean close() {
        if (state == RoomState.CLOSED) {
            return false;
        }

        state = RoomState.CLOSED;
        return true;
    }
}
