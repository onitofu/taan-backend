package taan.online.room.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import taan.online.account.model.AccountId;

public final class Room {
    private final RoomId id;
    private final UUID generation;
    private final int channelId;
    private final RoomSettings settings;
    private final RoomPassword password;
    private final List<RoomSlot> slots;
    private UUID ownerSessionId;
    private RoomState state = RoomState.WAITING;

    public Room(
        RoomId id,
        UUID generation,
        int channelId,
        RoomSettings settings,
        RoomPassword password,
        RoomParticipant owner
    ) {
        this(
            id,
            generation,
            channelId,
            settings,
            password,
            owner,
            IntStream.range(0, 8).mapToObj(index -> new RoomSlot(index, RoomSlotState.OPEN, null)).toList()
        );
    }

    Room(
        RoomId id,
        UUID generation,
        int channelId,
        RoomSettings settings,
        RoomPassword password,
        RoomParticipant owner,
        List<RoomSlot> slots
    ) {
        this.id = Objects.requireNonNull(id);
        this.generation = Objects.requireNonNull(generation);
        this.settings = Objects.requireNonNull(settings);
        this.password = password;
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
        return (int) slots.stream().filter(slot -> slot.state().occupied()).count();
    }

    public boolean contains(RoomParticipant participant) {
        return slots.stream().map(RoomSlot::participant).filter(Objects::nonNull).anyMatch(
            value -> value.sessionId().equals(participant.sessionId())
                || value.character().id().equals(participant.character().id())
                || value.character().accountId().equals(participant.character().accountId())
        );
    }

    public boolean protectedRoom() {
        return password != null;
    }

    public boolean acceptsPassword(String candidate) {
        return password == null || password.matches(candidate);
    }

    public Optional<RoomSlot> enter(RoomParticipant participant, String candidate) {
        if (state != RoomState.WAITING || playerCount() >= settings.capacity() || !acceptsPassword(candidate)) {
            return Optional.empty();
        }

        if (contains(participant)) {
            throw new IllegalArgumentException("Duplicate room participant");
        }

        long blue = slots.stream().filter(slot -> slot.state().occupied())
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

    public Optional<RoomTeamChange> changeTeam(UUID sessionId, RoomTeam desiredTeam) {
        Objects.requireNonNull(desiredTeam);

        if (state != RoomState.WAITING) {
            throw new IllegalStateException("Room team cannot be changed");
        }

        var source = slots.stream().filter(slot -> slot.participant() != null)
            .filter(slot -> slot.participant().sessionId().equals(sessionId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Room participant is not present"));

        if (source.state() == RoomSlotState.READY) {
            throw new IllegalStateException("Ready participant cannot change team");
        }

        if (source.team() == desiredTeam) {
            return Optional.empty();
        }

        var target = slots.stream().filter(slot -> slot.team() == desiredTeam && slot.state() == RoomSlotState.OPEN)
            .min(Comparator.comparingInt(RoomSlot::index));
        return target.map(
            slot -> {
                var destination = new RoomSlot(slot.index(), RoomSlotState.OCCUPIED, source.participant());
                var change = new RoomTeamChange(source, destination);
                slots.set(source.index(), new RoomSlot(source.index(), RoomSlotState.OPEN, null));
                slots.set(destination.index(), destination);
                return change;
            }
        );
    }

    public static boolean isSelectableWeapon(int weaponId) {
        return weaponId >= 0 && weaponId <= 11 || weaponId == 200;
    }

    public boolean changeWeapon(UUID sessionId, int weaponId) {
        if (state != RoomState.WAITING) {
            throw new IllegalStateException("Room weapon cannot be changed");
        }

        if (!isSelectableWeapon(weaponId)) {
            throw new IllegalArgumentException("Unsupported room weapon");
        }

        var slot = slots.stream().filter(value -> value.participant() != null)
            .filter(value -> value.participant().sessionId().equals(sessionId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Room participant is not present"));

        if (slot.state() == RoomSlotState.READY) {
            throw new IllegalStateException("Ready participant cannot change weapon");
        }

        var actor = slot.participant();

        if (actor.weaponId() == weaponId) {
            return false;
        }

        var updated = new RoomParticipant(actor.sessionId(), actor.character(), weaponId);
        slots.set(slot.index(), new RoomSlot(slot.index(), slot.state(), updated));
        return true;
    }

    public boolean setReady(AccountId accountId, boolean ready) {
        if (state != RoomState.WAITING) {
            throw new IllegalStateException("Room readiness cannot be changed");
        }

        var slot = slots.stream().filter(value -> value.participant() != null)
            .filter(value -> value.participant().character().accountId().equals(accountId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Room participant is not present"));

        if (ready && slot.participant().sessionId().equals(ownerSessionId)) {
            throw new IllegalStateException("Room owner cannot be ready");
        }

        var desired = ready ? RoomSlotState.READY : RoomSlotState.OCCUPIED;

        if (slot.state() == desired) {
            return false;
        }

        slots.set(slot.index(), new RoomSlot(slot.index(), desired, slot.participant()));
        return true;
    }

    public boolean setSlotAvailability(int index, RoomSlotState desiredState) {
        if (index < 0 || index >= slots.size() || desiredState == null || desiredState.occupied()) {
            throw new IllegalArgumentException("Invalid room slot availability");
        }

        var previous = slots.get(index);

        if (state != RoomState.WAITING || previous.participant() != null) {
            throw new IllegalStateException("Room slot availability cannot be changed");
        }

        if (previous.state() == desiredState) {
            return false;
        }

        slots.set(index, new RoomSlot(index, desiredState, null));
        return true;
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
            var promoted = slots.stream().filter(slot -> slot.state().occupied()).findFirst().orElseThrow();
            ownerSessionId = promoted.participant().sessionId();
            slots.set(promoted.index(), new RoomSlot(promoted.index(), RoomSlotState.OCCUPIED, promoted.participant()));
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
