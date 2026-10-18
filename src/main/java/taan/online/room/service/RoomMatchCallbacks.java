package taan.online.room.service;

import java.util.UUID;
import taan.online.room.service.model.RoomMatchProgressToken;

public interface RoomMatchCallbacks {
    boolean deliveryCompleted(RoomMatchProgressToken token, UUID recipient, boolean success);

    void deadlineExpired(RoomMatchProgressToken token);
}
