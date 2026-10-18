package taan.online;

import java.util.List;
import taan.online.match.controller.MatchPacketHandler;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.ConsumeMatchResultAction;
import taan.online.match.service.TerminateMatchAction;
import taan.online.match.service.TestMatchOutput;
import taan.online.room.model.RoomMode;
import taan.online.room.service.model.RoomMatchToken;

public final class MatchTerminalFixture implements AutoCloseable {
    public final MatchRoomFixture room;
    public final List<TestMatchOutput> outputs;
    public final MatchPacketHandler handler;
    public final RoomMatchToken token;

    public MatchTerminalFixture(int count, RoomMode mode, MatchPhase phase) {
        room = new MatchRoomFixture(count, mode);
        outputs = room.players.stream().map(
            id -> {
                var output = new TestMatchOutput();
                room.scenario.matches.subscribe(id, output);
                return output;
            }
        ).toList();
        var rooms = room.scenario.rooms;
        var matches = room.scenario.matches;
        handler = new MatchPacketHandler(
            rooms,
            matches,
            new TerminateMatchAction(rooms),
            new ConsumeMatchResultAction(rooms, matches)
        );

        if (mode.requiresCaptainSelection()) {
            room.captains();
        }

        token = rooms.reserveMatch(room.owner).token();

        if (phase != MatchPhase.RESERVED) {
            rooms.commitMatch(token);
        }

        if (phase == MatchPhase.PREVIEW) {
            rooms.previewStarted(token);
        }
    }

    @Override
    public void close() {
        room.close();
        room.players.forEach(room.scenario.matches::unsubscribe);
    }
}
