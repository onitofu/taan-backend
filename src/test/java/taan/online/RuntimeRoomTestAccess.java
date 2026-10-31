package taan.online;

import java.util.Map;
import taan.online.bootstrap.Application;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.MatchRegistry;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.room.model.CaptainSelectionPhase;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.service.RoomRegistry;
import taan.online.room.service.model.RoomSnapshot;

public final class RuntimeRoomTestAccess {
    private RuntimeRoomTestAccess() {
    }

    public static void awaitMatchAction(Application application, int roomId) throws ReflectiveOperationException {
        var registry = rooms(application);
        var matches = matches(registry);
        var owner = room(registry, roomId).ownerSessionId();
        var token = registry.matchContext(owner).token();
        TestAwait.until("match action publication", () -> matches.current(token)
            .filter(snapshot -> snapshot.phase() == MatchPhase.ACTION).isPresent());
    }

    public static void awaitCaptainFinalization(Application application, int id) throws ReflectiveOperationException {
        var registry = rooms(application);
        var field = RoomRegistry.class.getDeclaredField("rooms");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var rooms = (Map<RoomId, Room>) field.get(registry);
        TestAwait.until("captain selection finalization", 10000, () -> {
            synchronized (registry) {
                return rooms.get(new RoomId(id)).captainSelection().phase() == CaptainSelectionPhase.FINALIZED;
            }
        });
    }

    public static RoomRegistry rooms(Application application) throws ReflectiveOperationException {
        var field = Application.class.getDeclaredField("rooms");
        field.setAccessible(true);
        return (RoomRegistry) field.get(application);
    }

    public static MatchRegistry matches(RoomRegistry registry) throws ReflectiveOperationException {
        var matches = RoomRegistry.class.getDeclaredField("matches");
        matches.setAccessible(true);
        var operations = matches.get(registry);
        var lifecycle = operations.getClass().getDeclaredField("lifecycle");
        lifecycle.setAccessible(true);
        return (MatchRegistry) lifecycle.get(operations);
    }

    public static RoomSnapshot room(RoomRegistry registry, int id) throws ReflectiveOperationException {
        synchronized (registry) {
            var field = RoomRegistry.class.getDeclaredField("rooms");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var rooms = (Map<RoomId, Room>) field.get(registry);
            return RoomSnapshot.from(rooms.get(new RoomId(id)));
        }
    }

    public static MatchSnapshot matchSnapshot(Application app, int id) throws ReflectiveOperationException {
        var rooms = rooms(app);
        return matches(rooms).current(rooms.matchContext(room(rooms, id).ownerSessionId()).token()).orElseThrow();
    }
}
