package taan.online;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import taan.online.room.model.RoomMode;
import taan.online.room.service.model.RoomMatchDraft;
import taan.online.room.service.model.RoomMatchParticipant;
import taan.online.room.service.model.RoomMatchToken;

public final class MatchRoomFixture implements AutoCloseable {
    public final RoomScenario scenario;
    public final List<UUID> players = new ArrayList<>();
    public final List<RecordingRoomOutput> outputs = new ArrayList<>();
    public final UUID owner;

    public MatchRoomFixture(int count, RoomMode mode) {
        this(new RoomScenario(), count, mode);
    }

    public MatchRoomFixture(RoomScenario scenario, int count, RoomMode mode) {
        this.scenario = scenario;
        for (int index = 0; index < count; index++) {
            var id = scenario.player(index + 1, 1);
            var output = new RecordingRoomOutput();
            players.add(id);
            outputs.add(output);

            if (index == 0) {
                scenario.create.execute(id, RoomScenario.command(), output);
            } else {
                scenario.enter.execute(id, 1, "", output);
            }
        }

        owner = players.getFirst();
        int gameType = switch (mode) {
            case ITEM -> 0;
            case NO_ITEM -> 1;
            case CAPTAIN_ITEM -> 2;
            case CAPTAIN_NO_ITEM -> 3;
        };
        scenario.rooms.changeSettings(owner, 1, gameType, mode.usesItems() ? 2 : 0);
        players.stream().skip(1).forEach(id -> scenario.rooms.setReady(id, true));
    }

    public void captains() {
        scenario.rooms.beginCaptainSelection(owner);
        scenario.timers.advanceSeconds(5);
    }

    public RoomMatchDraft draft() {
        var room = scenario.rooms.current(owner).orElseThrow();
        var participants = room.slots().stream().filter(value -> value.participant() != null).map(
            slot -> new RoomMatchParticipant(
                scenario.rooms.membership(slot.participant().sessionId()).orElseThrow(),
                slot
            )
        ).toList();
        return new RoomMatchDraft(
            new RoomMatchToken(room.id(), room.generation(), UUID.randomUUID()),
            1,
            owner,
            room.settings(),
            participants,
            null
        );
    }

    @Override
    public void close() {
        scenario.close();
    }
}
