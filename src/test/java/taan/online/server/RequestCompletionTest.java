package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class RequestCompletionTest {
    @Test
    void ownsBatchBytesAndKeepsTheDeliveryReceiptPendingUntilFlush() {
        byte[] source = {1, 2, 3};
        var receipt = new CompletableFuture<Void>();
        var batch = new OutboundBatch(List.of(source), receipt);
        source[0] = 99;
        batch.roots().getFirst()[1] = 99;
        var completion = new RequestCompletion(ProtocolEvent.LOGIN_SUCCEEDED, receipt);

        assertEquals(ProtocolEvent.LOGIN_SUCCEEDED, completion.event());
        assertArrayEquals(new byte[]{1, 2, 3}, batch.roots().getFirst());
        assertEquals(7, batch.bytes());
        assertFalse(completion.delivery().toCompletableFuture().isDone());
        receipt.complete(null);
        assertTrue(completion.delivery().toCompletableFuture().isDone());
        assertTrue(
            new RequestCompletion(ProtocolEvent.KEEP_ALIVE_ACCEPTED, null)
                .delivery().toCompletableFuture().isDone()
        );
    }

    @Test
    void rejectsInputEventsFromBeingUsedAsWorkerOutcomes() {
        for (var event : new ProtocolEvent[]{ProtocolEvent.USER_LOGIN, ProtocolEvent.CONNECTION_CLOSED}) {
            assertThrows(IllegalArgumentException.class, () -> new RequestCompletion(event, null));
        }

        assertThrows(NullPointerException.class, () -> new RequestCompletion(null, null));
    }
}
