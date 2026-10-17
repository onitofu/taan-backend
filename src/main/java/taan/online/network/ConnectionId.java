package taan.online.network;

import java.util.UUID;

public record ConnectionId(UUID value) {
    public static ConnectionId create() {
        return new ConnectionId(UUID.randomUUID());
    }
}
