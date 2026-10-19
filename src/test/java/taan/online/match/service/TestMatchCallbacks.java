package taan.online.match.service;

import java.util.List;
import java.util.UUID;
import taan.online.room.service.RoomMatchCallbacks;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchTerminationMode;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchToken;

final class TestMatchCallbacks implements RoomMatchCallbacks {
    private final MatchRuntime runtime;
    boolean current = true;
    boolean rejectTermination;
    RoomMatchEndReason reason;

    TestMatchCallbacks(MatchRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean startCurrent(RoomMatchToken token) {
        return current;
    }

    @Override
    public RoomMatchTerminationResult terminateMatch(RoomMatchToken token, RoomMatchEndReason value, UUID requester) {
        if (rejectTermination) {
            return RoomMatchTerminationResult.ignored();
        }

        reason = value;
        runtime.stop();
        var terminal = new RoomMatchTermination(token, value, RoomMatchTerminationMode.ROLLBACK, List.of());
        return new RoomMatchTerminationResult(terminal, runtime.finish(() -> { }));
    }
}
