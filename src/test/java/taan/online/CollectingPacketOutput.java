package taan.online;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.protocol.PacketOutput;

public final class CollectingPacketOutput implements PacketOutput {
    public final List<String> closeReasons = new ArrayList<>();
    public final List<List<byte[]>> batches = new ArrayList<>();

    @Override
    public void close(String reason) {
        closeReasons.add(reason);
    }

    @Override
    public CompletionStage<Void> send(List<byte[]> roots) {
        batches.add(roots.stream().map(byte[]::clone).toList());
        return CompletableFuture.completedFuture(null);
    }
}
