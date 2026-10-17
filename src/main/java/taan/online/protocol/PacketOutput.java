package taan.online.protocol;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface PacketOutput {
    CompletionStage<Void> send(List<byte[]> roots);

    void close(String reason);
}
