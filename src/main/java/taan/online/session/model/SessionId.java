package taan.online.session.model;

import java.util.Objects;
import java.util.UUID;

public record SessionId(UUID value) {
    public SessionId {
        Objects.requireNonNull(value);
    }
}
