package taan.online.server;

import java.util.Objects;

record RequestCompletion(ProtocolEvent event, byte[] response) {
    RequestCompletion {
        Objects.requireNonNull(event);

        if (event.isRequest() || event == ProtocolEvent.CONNECTION_CLOSED) {
            throw new IllegalArgumentException("A request completion requires an outcome event");
        }

        response = response == null ? null : response.clone();
    }

    @Override
    public byte[] response() {
        return response == null ? null : response.clone();
    }
}
