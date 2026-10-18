package taan.online.room.service;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchProgressToken;
import taan.online.room.service.model.RoomMatchReservationOutcome;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomRegistryPhase;
import taan.online.room.service.model.RoomStartOutcome;
import taan.online.room.service.model.RoomStartResult;

final class RoomMatchCoordinator implements RoomMatchAccess, RoomMatchCallbacks {
    private static final EventLogger LOG = EventLogger.forClass(RoomMatchCoordinator.class);

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

            try {
                if (!matches.activate(token, this)) {
                    matches.rollback(token, "OUTPUT_UNAVAILABLE");
                    return starts.unavailable(id, "OUTPUT_UNAVAILABLE", check.captains());
                }

                var committed = matches.commit(token);

                if (committed.outcome() != RoomMatchReservationOutcome.COMMITTED) {
                    return starts.unavailable(id, committed.reason(), check.captains());
                }

                var delivery = matches.publish(matches.progress(token), id);
                return new RoomStartResult(RoomStartOutcome.STARTED, "STARTED", check.captains(), delivery);
            } catch (RuntimeException failure) {
                matches.terminate(token, RoomMatchEndReason.PREPARATION_FAILED, null);
                throw failure;
            }
        }
    }

    @Override
    public RoomMatchContext matchContext(UUID id) {
        synchronized (monitor) {
            return matches.context(id, phase.get());
        }
    }

    @Override
    public CompletionStage<Void> loadingDone(RoomMatchContext context, int slot, int x, int y) {
        synchronized (monitor) {
            if (!current(context) || expired(context.token())) {
                return CompletableFuture.completedFuture(null);
            }

            try {
                var next = matches.loadingDone(context, slot, x, y);

                if (next == null) {
                    return CompletableFuture.completedFuture(null);
                }

                return matches.publish(next, context.membership().sessionId());
            } catch (RuntimeException failure) {
                matches.terminate(context.token(), RoomMatchEndReason.PREPARATION_FAILED, null);
                throw failure;
            }
        }
    }

    @Override
    public boolean confirmPosition(RoomMatchContext context, int slot, int x, int y, int facing) {
        synchronized (monitor) {
            if (!current(context) || expired(context.token())) {
                return false;
            }

            return matches.confirmPosition(context, slot, x, y, facing);
        }
    }

    @Override
    public boolean reportEnemies(RoomMatchContext context) {
        synchronized (monitor) {
            return current(context) && !expired(context.token()) && matches.reportEnemies(context);
        }
    }

    @Override
    public RoomMatchTerminationResult terminateMatch(RoomMatchContext context, RoomMatchEndReason reason) {
        synchronized (monitor) {
            return matches.terminate(context, reason, phase.get());
        }
    }

    @Override
    public RoomMatchTerminationResult terminateMatch(RoomMatchToken token, RoomMatchEndReason reason) {
        synchronized (monitor) {
            return matches.terminate(token, reason, null);
        }
    }

    @Override
    public boolean deliveryCompleted(RoomMatchProgressToken token, UUID recipient, boolean success) {
        synchronized (monitor) {
            if (!Objects.equals(token, matches.progress(token.match())) || expired(token.match())) {
                return false;
            }

            if (!success) {
                matches.terminate(token.match(), RoomMatchEndReason.START_DELIVERY_FAILED, null);
                return true;
            }

            try {
                var next = matches.deliveryCompleted(token, recipient);

                if (next != null) {
                    matches.publish(next, null);
                }
            } catch (RuntimeException failure) {
                failed(token, failure);
            }

            return true;
        }
    }

    @Override
    public void deadlineExpired(RoomMatchProgressToken token) {
        synchronized (monitor) {
            if (Objects.equals(token, matches.progress(token.match()))) {
                expired(token.match());
            }
        }
    }

    private boolean current(RoomMatchContext context) {
        return context != null && context.equals(matches.context(context.membership().sessionId(), phase.get()));
    }

    private boolean expired(RoomMatchToken token) {
        var progress = matches.progress(token);
        var reason = progress == null ? null : matches.timeoutReason(progress);

        if (reason == null) {
            return false;
        }

        matches.terminate(token, reason, null);
        return true;
    }

    private void failed(RoomMatchProgressToken token, RuntimeException failure) {
        LOG.failure(RoomLogEvent.OPERATION_FAILED, failure);
        matches.terminate(token.match(), RoomMatchEndReason.PREPARATION_FAILED, null);
    }
}
