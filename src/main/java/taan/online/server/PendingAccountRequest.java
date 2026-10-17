package taan.online.server;

import java.util.UUID;
import taan.online.account.protocol.packet.AccountRequest;

record PendingAccountRequest(UUID operationId, AccountRequest request) {
}
