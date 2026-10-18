package taan.online;

import java.util.Map;
import taan.online.match.service.MatchRegistry;
import taan.online.bootstrap.Application;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.service.RoomRegistry;
import taan.online.room.service.model.RoomSnapshot;

public final class RuntimeRoomTestAccess {
    private RuntimeRoomTestAccess() {
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
}
