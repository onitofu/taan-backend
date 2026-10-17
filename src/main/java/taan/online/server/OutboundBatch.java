package taan.online.server;

import java.util.List;
import java.util.concurrent.CompletableFuture;

record OutboundBatch(List<byte[]> roots, CompletableFuture<Void> delivery) {
    OutboundBatch {
        roots = roots.stream().map(byte[]::clone).toList();
    }

    @Override
    public List<byte[]> roots() {
        return roots.stream().map(byte[]::clone).toList();
    }

    long bytes() {
        return roots.stream().mapToLong(root -> root.length + 4L).sum();
    }
}
