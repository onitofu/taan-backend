package taan.online.room.service;

import java.util.List;
import taan.online.room.service.model.RoomListing;

public interface RoomListingAccess {
    List<RoomListing> forChannel(int channelId);
}
