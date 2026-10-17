package taan.online.room.controller;

import java.util.List;

public record RoomReply(RoomReplyOutcome outcome, List<byte[]> responses) {
    public RoomReply {
        responses = responses.stream().map(byte[]::clone).toList();
    }

    @Override
    public List<byte[]> responses() {
        return responses.stream().map(byte[]::clone).toList();
    }
}
