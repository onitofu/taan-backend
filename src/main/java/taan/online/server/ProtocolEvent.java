package taan.online.server;

enum ProtocolEvent {
    CHECK_ACCOUNT,
    CREATE_ACCOUNT,
    FIRST_PACKET,
    USER_LOGIN,
    KEEP_ALIVE,
    ACCOUNT_CHECK_COMPLETED,
    REGISTRATION_CREATED,
    REGISTRATION_REJECTED,
    HANDSHAKE_COMPLETED,
    LOGIN_SUCCEEDED,
    LOGIN_REJECTED,
    LOGIN_IGNORED,
    KEEP_ALIVE_ACCEPTED,
    CONNECTION_CLOSED;

    boolean isRequest() {
        return switch (this) {
            case CHECK_ACCOUNT, CREATE_ACCOUNT, FIRST_PACKET, USER_LOGIN, KEEP_ALIVE -> true;
            default -> false;
        };
    }
}
