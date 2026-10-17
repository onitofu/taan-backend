package taan.online.server;

import java.util.UUID;

record PendingRequest(UUID operationId, int type, byte[] root) {
    PendingRequest {
        root = root.clone();
    }

    @Override
    public byte[] root() {
        return root.clone();
    }
}
