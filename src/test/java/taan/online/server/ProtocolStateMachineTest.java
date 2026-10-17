package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProtocolStateMachineTest {
    private static final String TRANSITIONS = """
        INITIAL CHECK_ACCOUNT PROCESS INITIAL
        INITIAL CREATE_ACCOUNT PROCESS INITIAL
        INITIAL FIRST_PACKET PROCESS INITIAL
        INITIAL ACCOUNT_CHECK_COMPLETED CONTINUE PRELIMINARY
        INITIAL REGISTRATION_CREATED CLOSE_AFTER_REPLY TERMINATED
        INITIAL REGISTRATION_REJECTED CONTINUE PRELIMINARY
        INITIAL HANDSHAKE_COMPLETED CONTINUE AWAITING_LOGIN
        PRELIMINARY CHECK_ACCOUNT PROCESS PRELIMINARY
        PRELIMINARY CREATE_ACCOUNT PROCESS PRELIMINARY
        PRELIMINARY ACCOUNT_CHECK_COMPLETED CONTINUE PRELIMINARY
        PRELIMINARY REGISTRATION_CREATED CLOSE_AFTER_REPLY TERMINATED
        PRELIMINARY REGISTRATION_REJECTED CONTINUE PRELIMINARY
        AWAITING_LOGIN USER_LOGIN PROCESS AWAITING_LOGIN
        AWAITING_LOGIN KEEP_ALIVE PROCESS AWAITING_LOGIN
        AWAITING_LOGIN LOGIN_SUCCEEDED CONTINUE AUTHENTICATED
        AWAITING_LOGIN LOGIN_REJECTED CLOSE_AFTER_REPLY TERMINATED
        AWAITING_LOGIN LOGIN_IGNORED IGNORE AWAITING_LOGIN
        AWAITING_LOGIN KEEP_ALIVE_ACCEPTED CONTINUE AWAITING_LOGIN
        AUTHENTICATED USER_LOGIN IGNORE AUTHENTICATED
        AUTHENTICATED KEEP_ALIVE PROCESS AUTHENTICATED
        AUTHENTICATED LOGIN_IGNORED IGNORE AUTHENTICATED
        AUTHENTICATED KEEP_ALIVE_ACCEPTED CONTINUE AUTHENTICATED
        AUTHENTICATED CONFIRM_NAME PROCESS AUTHENTICATED
        AUTHENTICATED SELECT_CHARACTER PROCESS AUTHENTICATED
        AUTHENTICATED CHARACTER_SELECTED CONTINUE CHARACTER_SELECTED
        AUTHENTICATED CHARACTER_SELECTION_REJECTED CLOSE_WITHOUT_REPLY TERMINATED
        AUTHENTICATED NEW_CHARACTER PROCESS AUTHENTICATED
        AUTHENTICATED DELETE_CHARACTER PROCESS AUTHENTICATED
        AUTHENTICATED NAME_CHECK_COMPLETED CONTINUE AUTHENTICATED
        AUTHENTICATED CHARACTER_CREATED CONTINUE AUTHENTICATED
        AUTHENTICATED CHARACTER_DELETED CONTINUE AUTHENTICATED
        AUTHENTICATED CHARACTER_REJECTED CONTINUE AUTHENTICATED
        AUTHENTICATED CHARACTER_REQUEST_IGNORED IGNORE AUTHENTICATED
        CHARACTER_SELECTED USER_LOGIN IGNORE CHARACTER_SELECTED
        CHARACTER_SELECTED LOGIN_IGNORED IGNORE CHARACTER_SELECTED
        CHARACTER_SELECTED SELECT_CHARACTER IGNORE CHARACTER_SELECTED
        CHARACTER_SELECTED KEEP_ALIVE PROCESS CHARACTER_SELECTED
        CHARACTER_SELECTED KEEP_ALIVE_ACCEPTED CONTINUE CHARACTER_SELECTED
        CHARACTER_SELECTED GET_CHANNEL_LIST PROCESS CHARACTER_SELECTED
        CHARACTER_SELECTED UPDATE_CASH PROCESS CHARACTER_SELECTED
        CHARACTER_SELECTED CLIENT_LOGGING PROCESS CHARACTER_SELECTED
        CHARACTER_SELECTED SELECTED_REQUEST_COMPLETED CONTINUE CHARACTER_SELECTED
        CHARACTER_SELECTED SELECTED_REQUEST_IGNORED IGNORE CHARACTER_SELECTED
        """;
    private static final Set<ProtocolEvent> REQUESTS = Set.of(
        ProtocolEvent.CHECK_ACCOUNT,
        ProtocolEvent.CREATE_ACCOUNT,
        ProtocolEvent.FIRST_PACKET,
        ProtocolEvent.USER_LOGIN,
        ProtocolEvent.KEEP_ALIVE,
        ProtocolEvent.CONFIRM_NAME,
        ProtocolEvent.NEW_CHARACTER,
        ProtocolEvent.DELETE_CHARACTER,
        ProtocolEvent.SELECT_CHARACTER,
        ProtocolEvent.GET_CHANNEL_LIST,
        ProtocolEvent.UPDATE_CASH,
        ProtocolEvent.CLIENT_LOGGING
    );

    @Test
    void checksEveryPhaseAndEventAgainstTheProtocolContract() {
        Map<String, String[]> expected = new HashMap<>();
        TRANSITIONS.lines().forEach(
            line -> {
                String[] values = line.split(" ");
                expected.put(values[0] + "/" + values[1], values);
            }
        );

        for (var phase : ProtocolPhase.values()) {
            for (var event : ProtocolEvent.values()) {
                var machine = machineAt(phase);
                String key = phase + "/" + event;

                if (event == ProtocolEvent.CONNECTION_CLOSED) {
                    assertEquals(ProtocolDecision.IGNORE, machine.on(event), key);
                    assertEquals(ProtocolPhase.TERMINATED, machine.phase(), key);
                } else if (expected.containsKey(key)) {
                    var transition = expected.get(key);

                    assertEquals(ProtocolDecision.valueOf(transition[2]), machine.on(event), key);
                    assertEquals(ProtocolPhase.valueOf(transition[3]), machine.phase(), key);
                } else if (REQUESTS.contains(event)) {
                    assertEquals(ProtocolDecision.REJECT, machine.on(event), key);
                    assertEquals(phase, machine.phase(), key);
                } else {
                    assertThrows(IllegalStateException.class, () -> machine.on(event), key);
                    assertEquals(phase, machine.phase(), key);
                }
            }
        }
    }

    @Test
    void refusesAuthenticationAfterLoginRejectionAndRepeatedClosure() {
        var machine = machineAt(ProtocolPhase.AWAITING_LOGIN);

        assertEquals(ProtocolDecision.CLOSE_AFTER_REPLY, machine.on(ProtocolEvent.LOGIN_REJECTED));
        assertEquals(ProtocolPhase.TERMINATED, machine.phase());
        assertThrows(IllegalStateException.class, () -> machine.on(ProtocolEvent.LOGIN_SUCCEEDED));
        assertEquals(ProtocolDecision.IGNORE, machine.on(ProtocolEvent.CONNECTION_CLOSED));
        assertEquals(ProtocolDecision.IGNORE, machine.on(ProtocolEvent.CONNECTION_CLOSED));
        assertEquals(ProtocolPhase.TERMINATED, machine.phase());
    }

    @Test
    void leavesRequestExecutionAndStateMutationToTheConnectionOwner() {
        var machine = new ProtocolStateMachine();

        assertEquals(ProtocolDecision.PROCESS, machine.on(ProtocolEvent.FIRST_PACKET));
        assertEquals(ProtocolPhase.INITIAL, machine.phase());
        assertEquals(ProtocolDecision.CONTINUE, machine.on(ProtocolEvent.HANDSHAKE_COMPLETED));
        assertEquals(ProtocolDecision.PROCESS, machine.on(ProtocolEvent.USER_LOGIN));
        assertEquals(ProtocolPhase.AWAITING_LOGIN, machine.phase());
        assertEquals(ProtocolDecision.CONTINUE, machine.on(ProtocolEvent.LOGIN_SUCCEEDED));
        assertEquals(ProtocolPhase.AUTHENTICATED, machine.phase());
    }

    private static ProtocolStateMachine machineAt(ProtocolPhase phase) {
        var machine = new ProtocolStateMachine();

        switch (phase) {
            case INITIAL -> { }
            case PRELIMINARY -> machine.on(ProtocolEvent.ACCOUNT_CHECK_COMPLETED);
            case AWAITING_LOGIN -> machine.on(ProtocolEvent.HANDSHAKE_COMPLETED);
            case AUTHENTICATED -> {
                machine.on(ProtocolEvent.HANDSHAKE_COMPLETED);
                machine.on(ProtocolEvent.LOGIN_SUCCEEDED);
            }
            case CHARACTER_SELECTED -> {
                machine.on(ProtocolEvent.HANDSHAKE_COMPLETED);
                machine.on(ProtocolEvent.LOGIN_SUCCEEDED);
                machine.on(ProtocolEvent.CHARACTER_SELECTED);
            }
            case TERMINATED -> machine.on(ProtocolEvent.CONNECTION_CLOSED);
            default -> throw new AssertionError(phase);
        }

        assertEquals(phase, machine.phase());
        return machine;
    }
}
