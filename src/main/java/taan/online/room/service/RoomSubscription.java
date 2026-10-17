package taan.online.room.service;

import taan.online.room.service.model.RoomMembership;

record RoomSubscription(RoomMembership membership, RoomPresenceOutput output) {
}
