package taan.online.room.service;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchReservationOutcome;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomRegistryPhase;
import taan.online.room.service.model.RoomStartOutcome;
import taan.online.room.service.model.RoomStartResult;

final class RoomMatchCoordinator implements RoomMatchCallbacks {
    private final Object monitor;
    private final RoomMatchOperations matches;
    private final RoomStartOperations starts;
    private final Supplier<RoomRegistryPhase> phase;
    private final Function<UUID, String> rejection;

    RoomMatchCoordinator(
        Object monitor,
        RoomMatchOperations matches,
        RoomStartOperations starts,
        Supplier<RoomRegistryPhase> phase,
        Function<UUID, String> rejection
    ) {
        this.monitor = monitor;
        this.matches = matches;
        this.starts = starts;
        this.phase = phase;
        this.rejection = rejection;
    }

    RoomStartResult start(UUID id) {
        synchronized (monitor) {
            if (rejection.apply(id) == null && matches.activeOwner(id)) {
                return new RoomStartResult(RoomStartOutcome.ALREADY_STARTED, "ALREADY_STARTED");
            }

            var check = starts.start(id, rejection.apply(id));

            if (check.outcome() != RoomStartOutcome.VALIDATED) {
                return check;
            }

            var reserved = matches.reserve(id, rejection.apply(id));

            if (reserved.outcome() != RoomMatchReservationOutcome.RESERVED) {
                return starts.unavailable(id, reserved.reason(), check.captains());
            }

            var token = reserved.token();

            if (!matches.activate(token, this)) {
                matches.rollback(token, "OUTPUT_UNAVAILABLE");
                return starts.unavailable(id, "OUTPUT_UNAVAILABLE", check.captains());
            }

            String invalid = matches.startRejection(token);

            if (invalid != null) {
                matches.rollback(token, invalid);
                return starts.unavailable(id, invalid, check.captains());
            }

            var delivery = matches.commit(token).thenCompose(result -> matches.publish(token));
            return new RoomStartResult(RoomStartOutcome.STARTED, "STARTED", check.captains(), delivery);
        }
    }

    @Override
    public RoomMatchTerminationResult terminateMatch(RoomMatchToken token, RoomMatchEndReason reason, UUID requester) {
        synchronized (monitor) {
            return matches.terminate(token, reason, requester);
        }
    }

    @Override
    public boolean startCurrent(RoomMatchToken token) {
        synchronized (monitor) {
            return phase.get() == RoomRegistryPhase.OPEN && matches.startRejection(token) == null;
        }
    }
}
