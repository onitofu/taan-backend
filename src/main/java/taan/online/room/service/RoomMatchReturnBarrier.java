package taan.online.room.service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import taan.online.room.service.model.RoomMatchTerminationMode;
import taan.online.room.service.model.RoomMembership;

final class RoomMatchReturnBarrier {
    private final Set<RoomMembership> departures = new HashSet<>();
    private final CompletableFuture<Void> queued = new CompletableFuture<>();
    private RoomMatchTerminationMode mode;
    private RoomMatchReturnPhase phase = RoomMatchReturnPhase.WAITING_OUTCOME;

    boolean request(RoomMembership member) {
        return departures.add(member);
    }

    boolean beginReturn() {
        if (phase == RoomMatchReturnPhase.QUEUING_RETURN) {
            return false;
        }

        phase = RoomMatchReturnPhase.QUEUING_RETURN;
        return true;
    }

    void mode(RoomMatchTerminationMode value) {
        mode = value;
    }

    boolean rolledBack() {
        return mode == RoomMatchTerminationMode.ROLLBACK;
    }

    void afterQueued(Runnable work) {
        queued.thenRun(work);
    }

    void complete() {
        queued.complete(null);
    }
}
