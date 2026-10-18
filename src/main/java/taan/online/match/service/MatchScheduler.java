package taan.online.match.service;

import java.util.UUID;
import taan.online.match.service.model.MatchDeadline;
import taan.online.room.service.model.RoomMatchProgressToken;

public interface MatchScheduler {
    void schedule(MatchDeadline deadline, Runnable command, Runnable rejected);

    void execute(RoomMatchProgressToken token, Runnable command, Runnable rejected);

    void cancel(UUID matchId);
}
