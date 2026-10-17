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
            entry(ProtocolEvent.KEEP_ALIVE_ACCEPTED, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.CONFIRM_NAME, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.NEW_CHARACTER, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.SELECT_CHARACTER, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.CHARACTER_SELECTED, ProtocolPhase.CHARACTER_SELECTED),
            entry(ProtocolEvent.CHARACTER_SELECTION_REJECTED, ProtocolPhase.TERMINATED),
            entry(ProtocolEvent.DELETE_CHARACTER, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.NAME_CHECK_COMPLETED, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.CHARACTER_CREATED, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.CHARACTER_DELETED, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.CHARACTER_REJECTED, ProtocolPhase.AUTHENTICATED),
            entry(ProtocolEvent.CHARACTER_REQUEST_IGNORED, ProtocolPhase.AUTHENTICATED)
        ),
        ProtocolPhase.CHARACTER_SELECTED,
        selectedTransitions(ProtocolPhase.CHARACTER_SELECTED),
        ProtocolPhase.CHANNEL_SELECTED,
        selectedTransitions(ProtocolPhase.CHANNEL_SELECTED),
        ProtocolPhase.LOBBY,
        selectedTransitions(ProtocolPhase.LOBBY),
        ProtocolPhase.ROOM,
        roomTransitions(),
        ProtocolPhase.TERMINATED,
        Map.of()
    );

    private static Map<ProtocolEvent, ProtocolPhase> selectedTransitions(ProtocolPhase phase) {
        return Map.ofEntries(
            entry(ProtocolEvent.ROOM_CREATE, phase),
            entry(ProtocolEvent.ROOM_ENTER, phase),
            entry(ProtocolEvent.ROOM_LEAVE, phase),
            entry(ProtocolEvent.ROOM_CREATED, ProtocolPhase.ROOM),
            entry(ProtocolEvent.ROOM_LEFT, ProtocolPhase.CHANNEL_SELECTED),
            entry(ProtocolEvent.ROOM_LEAVE_REPLAYED, phase),
            entry(ProtocolEvent.ROOM_REQUEST_COMPLETED, phase),
            entry(ProtocolEvent.ROOM_REQUEST_IGNORED, phase),
            entry(ProtocolEvent.USER_LOGIN, phase),
            entry(ProtocolEvent.LOGIN_IGNORED, phase),
            entry(ProtocolEvent.SELECT_CHARACTER, phase),
            entry(ProtocolEvent.KEEP_ALIVE, phase),
            entry(ProtocolEvent.KEEP_ALIVE_ACCEPTED, phase),
            entry(ProtocolEvent.GET_CHANNEL_LIST, phase),
            entry(ProtocolEvent.UPDATE_CASH, phase),
            entry(ProtocolEvent.CLIENT_LOGGING, phase),
            entry(ProtocolEvent.MOVE_INVENTORY_ITEM, phase),
            entry(ProtocolEvent.INVENTORY_REQUEST_COMPLETED, phase),
            entry(ProtocolEvent.INVENTORY_REQUEST_IGNORED, phase),
            entry(ProtocolEvent.SELECTED_REQUEST_COMPLETED, phase),
            entry(ProtocolEvent.SELECTED_REQUEST_IGNORED, phase),
            entry(ProtocolEvent.MOVE_TO, phase),
            entry(ProtocolEvent.LOBBY_ENTER, phase),
            entry(ProtocolEvent.LOBBY_LEAVE, phase),
            entry(ProtocolEvent.CHANNEL_MOVED, ProtocolPhase.CHANNEL_SELECTED),
            entry(ProtocolEvent.CHANNEL_MOVE_REJECTED, phase),
            entry(ProtocolEvent.LOBBY_ENTERED, ProtocolPhase.LOBBY),
            entry(ProtocolEvent.LOBBY_UNAVAILABLE, ProtocolPhase.CHARACTER_SELECTED),
            entry(ProtocolEvent.LOBBY_LEFT_MEMBER, ProtocolPhase.CHANNEL_SELECTED),
            entry(ProtocolEvent.LOBBY_LEFT_UNBOUND, ProtocolPhase.CHARACTER_SELECTED),
            entry(ProtocolEvent.CHANNEL_REQUEST_IGNORED, phase)
        );
    }

    private static Map<ProtocolEvent, ProtocolPhase> roomTransitions() {
        return Map.ofEntries(
            entry(ProtocolEvent.USER_LOGIN, ProtocolPhase.ROOM),
            entry(ProtocolEvent.LOGIN_IGNORED, ProtocolPhase.ROOM),
            entry(ProtocolEvent.SELECT_CHARACTER, ProtocolPhase.ROOM),
            entry(ProtocolEvent.KEEP_ALIVE, ProtocolPhase.ROOM),
            entry(ProtocolEvent.KEEP_ALIVE_ACCEPTED, ProtocolPhase.ROOM),
            entry(ProtocolEvent.UPDATE_CASH, ProtocolPhase.ROOM),
            entry(ProtocolEvent.CLIENT_LOGGING, ProtocolPhase.ROOM),
            entry(ProtocolEvent.SELECTED_REQUEST_COMPLETED, ProtocolPhase.ROOM),
            entry(ProtocolEvent.SELECTED_REQUEST_IGNORED, ProtocolPhase.ROOM),
            entry(ProtocolEvent.LOBBY_LEAVE, ProtocolPhase.ROOM),
            entry(ProtocolEvent.LOBBY_LEFT_MEMBER, ProtocolPhase.ROOM),
            entry(ProtocolEvent.LOBBY_LEFT_UNBOUND, ProtocolPhase.ROOM),
            entry(ProtocolEvent.CHANNEL_REQUEST_IGNORED, ProtocolPhase.ROOM),
            entry(ProtocolEvent.ROOM_CREATE, ProtocolPhase.ROOM),
            entry(ProtocolEvent.ROOM_ENTER, ProtocolPhase.ROOM),
            entry(ProtocolEvent.ROOM_LEAVE, ProtocolPhase.ROOM),
            entry(ProtocolEvent.ROOM_START, ProtocolPhase.ROOM),
            entry(ProtocolEvent.ROOM_LEFT, ProtocolPhase.CHANNEL_SELECTED),
            entry(ProtocolEvent.ROOM_LEAVE_REPLAYED, ProtocolPhase.ROOM),
            entry(ProtocolEvent.ROOM_REQUEST_COMPLETED, ProtocolPhase.ROOM),
            entry(ProtocolEvent.ROOM_REQUEST_IGNORED, ProtocolPhase.ROOM)
        );
    }

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
            case USER_LOGIN -> phase != ProtocolPhase.AWAITING_LOGIN
                ? ProtocolDecision.IGNORE : ProtocolDecision.PROCESS;
            case SELECT_CHARACTER -> phase != ProtocolPhase.AUTHENTICATED
                ? ProtocolDecision.IGNORE : ProtocolDecision.PROCESS;
            case CHARACTER_SELECTION_REJECTED -> ProtocolDecision.CLOSE_WITHOUT_REPLY;
            case CHECK_ACCOUNT, CREATE_ACCOUNT, FIRST_PACKET, KEEP_ALIVE,
                CONFIRM_NAME, NEW_CHARACTER, DELETE_CHARACTER, GET_CHANNEL_LIST, UPDATE_CASH,
                CLIENT_LOGGING, MOVE_INVENTORY_ITEM, MOVE_TO, LOBBY_ENTER, LOBBY_LEAVE,
                ROOM_CREATE, ROOM_ENTER, ROOM_LEAVE, ROOM_START -> ProtocolDecision.PROCESS;
            case LOGIN_IGNORED, CHARACTER_REQUEST_IGNORED, SELECTED_REQUEST_IGNORED, CHANNEL_REQUEST_IGNORED,
                INVENTORY_REQUEST_IGNORED, ROOM_REQUEST_IGNORED ->
                ProtocolDecision.IGNORE;
            case REGISTRATION_CREATED, LOGIN_REJECTED -> ProtocolDecision.CLOSE_AFTER_REPLY;
            default -> ProtocolDecision.CONTINUE;
        };
    }
}
