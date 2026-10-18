package taan.online.server;

import java.util.UUID;
import taan.online.match.controller.MatchRequestContext;

record PendingRequest(UUID operationId, int type, byte[] root, MatchRequestContext matchContext) {
    PendingRequest {
        root = root.clone();
    }

    @Override
    public byte[] root() {
        return root.clone();
    }
}
