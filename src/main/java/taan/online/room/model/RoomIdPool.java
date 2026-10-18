package taan.online.room.model;

import java.util.BitSet;
import java.util.Optional;

public final class RoomIdPool {
    private static final int MAX_ID = 65535;

    private final BitSet allocated = new BitSet(MAX_ID + 1);

    public Optional<RoomId> allocate() {
        int value = allocated.nextClearBit(1);

        if (value > MAX_ID) {
            return Optional.empty();
        }

        allocated.set(value);
        return Optional.of(new RoomId(value));
    }

    public void release(RoomId id) {
        allocated.clear(id.value());
    }
}
