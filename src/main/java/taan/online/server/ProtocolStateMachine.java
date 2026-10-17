package taan.online.server;

import static java.util.Map.entry;

import java.util.Map;

final class ProtocolStateMachine {
    private static final Map<ProtocolPhase, Map<ProtocolEvent, ProtocolPhase>> TRANSITIONS = Map.of(
        ProtocolPhase.INITIAL,
        Map.ofEntries(
            entry(ProtocolEvent.CHECK_ACCOUNT, ProtocolPhase.INITIAL),
            entry(ProtocolEvent.CREATE_ACCOUNT, ProtocolPhase.INITIAL),
            entry(ProtocolEvent.FIRST_PACKET, ProtocolPhase.INITIAL),
            entry(ProtocolEvent.ACCOUNT_CHECK_COMPLETED, ProtocolPhase.PRELIMINARY),
            entry(ProtocolEvent.REGISTRATION_CREATED, ProtocolPhase.TERMINATED),
            entry(ProtocolEvent.REGISTRATION_REJECTED, ProtocolPhase.PRELIMINARY),
            entry(ProtocolEvent.HANDSHAKE_COMPLETED, ProtocolPhase.AWAITING_LOGIN)
        ),
        ProtocolPhase.PRELIMINARY,
        Map.ofEntries(
            entry(ProtocolEvent.CHECK_ACCOUNT, ProtocolPhase.PRELIMINARY),
            entry(ProtocolEvent.CREATE_ACCOUNT, ProtocolPhase.PRELIMINARY),
            entry(ProtocolEvent.ACCOUNT_CHECK_COMPLETED, ProtocolPhase.PRELIMINARY),
            entry(ProtocolEvent.REGISTRATION_CREATED, ProtocolPhase.TERMINATED),
            entry(ProtocolEvent.REGISTRATION_REJECTED, ProtocolPhase.PRELIMINARY)
        ),
        ProtocolPhase.AWAITING_LOGIN,
        Map.ofEntries(
            entry(ProtocolEvent.USER_LOGIN, ProtocolPhase.AWAITING_LOGIN),
            entry(ProtocolEvent.KEEP_ALIVE, ProtocolPhase.AWAITING_LOGIN),
            entry(ProtocolEvent.LOGIN_SUCCEEDED, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.LOGIN_REJECTED, ProtocolPhase.TERMINATED),
            entry(ProtocolEvent.LOGIN_IGNORED, ProtocolPhase.AWAITING_LOGIN),
            entry(ProtocolEvent.KEEP_ALIVE_ACCEPTED, ProtocolPhase.AWAITING_LOGIN)
        ),
        ProtocolPhase.AUTHENTICATED,
        Map.ofEntries(
            entry(ProtocolEvent.USER_LOGIN, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.KEEP_ALIVE, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.LOGIN_IGNORED, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.KEEP_ALIVE_ACCEPTED, ProtocolPhase.AUTHENTICATED)
        ),
        ProtocolPhase.TERMINATED,
        Map.of()
    );

    private ProtocolPhase phase = ProtocolPhase.INITIAL;

    ProtocolPhase phase() {
        return phase;
    }

    ProtocolDecision on(ProtocolEvent event) {
        if (event == ProtocolEvent.CONNECTION_CLOSED) {
            phase = ProtocolPhase.TERMINATED;
            return ProtocolDecision.IGNORE;
        }

        ProtocolPhase next = TRANSITIONS.get(phase).get(event);

        if (next == null) {
            if (event.isRequest()) {
                return ProtocolDecision.REJECT;
            }

            throw new IllegalStateException("Invalid protocol completion: " + phase + "/" + event);
        }

        phase = next;

        return switch (event) {
            case USER_LOGIN -> phase == ProtocolPhase.AUTHENTICATED
                ? ProtocolDecision.IGNORE : ProtocolDecision.PROCESS;
            case CHECK_ACCOUNT, CREATE_ACCOUNT, FIRST_PACKET, KEEP_ALIVE -> ProtocolDecision.PROCESS;
            case LOGIN_IGNORED -> ProtocolDecision.IGNORE;
            case REGISTRATION_CREATED, LOGIN_REJECTED -> ProtocolDecision.CLOSE_AFTER_REPLY;
            default -> ProtocolDecision.CONTINUE;
        };
    }
}
