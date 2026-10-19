package taan.online;

import java.util.List;
import java.util.concurrent.CompletionStage;
import taan.online.match.service.TestMatchOutput;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.room.model.RoomMode;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomStartResult;

public final class MatchBootstrapFixture implements AutoCloseable {
    public final MatchRoomFixture room;
    public final List<TestMatchOutput> outputs;

    public MatchBootstrapFixture(int count, RoomMode mode) {
        room = new MatchRoomFixture(count, mode);
        outputs = room.players.stream().map(
            id -> {
                var output = new TestMatchOutput();
                room.scenario.matches.subscribe(id, output);
                return output;
            }
        ).toList();

        if (mode.requiresCaptainSelection()) {
            room.captains();
        }
    }

    public RoomStartResult start() {
        return room.scenario.start.execute(room.owner);
    }

    public RoomMatchToken token() {
        return context(0).token();
    }

    public RoomMatchContext context(int index) {
        return room.scenario.rooms.matchContext(room.players.get(index));
    }

    public MatchSnapshot snapshot() {
        return room.scenario.matches.current(token()).orElseThrow();
    }

    public CompletionStage<Void> ack(int index) {
        var member = snapshot().participants().stream().filter(
            value -> value.sessionId().equals(room.players.get(index))
        )
            .findFirst().orElseThrow();
        return room.scenario.matches.loadingDone(
            context(index),
            member.slotIndex(),
            member.spawn().settledX(),
            member.spawn().settledY()
        ).thenApply(ignored -> null);
    }

    @Override
    public void close() {
        room.close();
        room.players.forEach(room.scenario.matches::unsubscribe);
    }
}
