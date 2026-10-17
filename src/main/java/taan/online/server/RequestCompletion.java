package taan.online.server;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.protocol.PacketOutput;

record RequestCompletion(ProtocolEvent event, CompletionStage<Void> delivery) {
    RequestCompletion {
        Objects.requireNonNull(event);

        if (event.isRequest() || event == ProtocolEvent.CONNECTION_CLOSED) {
            throw new IllegalArgumentException("A request completion requires an outcome event");
        }

        delivery = delivery == null ? CompletableFuture.completedFuture(null) : delivery;
    }

    static RequestCompletion reply(PacketOutput output, ProtocolEvent event, byte[] response) {
        return new RequestCompletion(event, response == null ? null : output.send(List.of(response)));
    }

    static RequestCompletion batch(PacketOutput output, ProtocolEvent event, List<byte[]> responses) {
        return new RequestCompletion(event, output.send(responses));
    }
}
