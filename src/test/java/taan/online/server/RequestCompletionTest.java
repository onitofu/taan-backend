package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RequestCompletionTest {
    @Test
    void ownsResponseBytesAcrossTheWorkerAndEventLoopBoundary() {
        byte[] source = {1, 2, 3};
        var completion = new RequestCompletion(ProtocolEvent.LOGIN_SUCCEEDED, source);
        source[0] = 99;
        byte[] received = completion.response();
        received[1] = 99;

        assertEquals(ProtocolEvent.LOGIN_SUCCEEDED, completion.event());
        assertArrayEquals(new byte[]{1, 2, 3}, completion.response());
        assertNull(new RequestCompletion(ProtocolEvent.KEEP_ALIVE_ACCEPTED, null).response());
    }

    @Test
    void rejectsInputEventsFromBeingUsedAsWorkerOutcomes() {
        for (var event : new ProtocolEvent[]{ProtocolEvent.USER_LOGIN, ProtocolEvent.CONNECTION_CLOSED}) {
            assertThrows(IllegalArgumentException.class, () -> new RequestCompletion(event, null));
        }

        assertThrows(NullPointerException.class, () -> new RequestCompletion(null, null));
    }
}
