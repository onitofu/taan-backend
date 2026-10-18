package taan.online.room.service;

import java.util.UUID;
import taan.online.room.service.model.RoomSelectionToken;

public interface RoomSelectionScheduler {
    void schedule(RoomSelectionToken token, long delayNanos, Runnable command);

    void cancel(UUID selectionId);
}
