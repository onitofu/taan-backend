package taan.online.session.controller;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.connectionId;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.outcome;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import taan.online.account.model.AccountGender;
import taan.online.account.model.LoginName;
import taan.online.account.service.model.ClientCredential;
import taan.online.character.controller.CharacterCatalogMapper;
import taan.online.character.service.ListCharactersAction;
import taan.online.logging.EventLogger;
import taan.online.session.logging.SessionLogEvent;
import taan.online.session.model.SessionId;
import taan.online.session.model.SessionPhase;
import taan.online.session.protocol.codec.UserLoginRequestCodec;
import taan.online.session.protocol.packet.PqUserLogin;
import taan.online.session.protocol.packet.PrUserLogin;
import taan.online.session.service.LoginAction;
import taan.online.session.service.SessionRegistry;
import taan.online.session.service.model.LoginCommand;
import taan.online.session.service.model.LoginRejected;
import taan.online.session.service.model.LoginResult;
import taan.online.session.service.model.LoginSucceeded;

public final class UserLoginPacketHandler {
    private static final EventLogger LOG = EventLogger.forClass(UserLoginPacketHandler.class);

    private final LoginAction login;
    private final SessionRegistry sessions;
    private final ListCharactersAction characters;
    private SessionId id;
    private UUID connection;

    public UserLoginPacketHandler(LoginAction login, SessionRegistry sessions, ListCharactersAction characters) {
        this.login = login;
        this.sessions = sessions;
        this.characters = characters;
    }

    public UUID open(UUID connectionId) {
        connection = connectionId;
        id = sessions.open();
        return id.value();
    }

    public Optional<PrUserLogin> handle(byte[] root) {
        long started = System.nanoTime();

        if (sessions.phase(id) != SessionPhase.ANONYMOUS) {
            LOG.log(SessionLogEvent.LOGIN_IGNORED, reason("SESSION_NOT_ANONYMOUS"));
            return Optional.empty();
        }

        PqUserLogin packet = new UserLoginRequestCodec().decode(root);
        LoginCommand command;

        if (packet.accounts().size() != 1) {
            return rejected(0, "UNSUPPORTED_ACCOUNT_COUNT", started);
        }

        var account = packet.accounts().getFirst();

        try {
            command = new LoginCommand(new LoginName(account.login()), new ClientCredential(account.credential()));
        } catch (IllegalArgumentException failure) {
            return rejected(0, "INVALID_LOGIN_INPUT", started);
        }

        LoginResult result = login.execute(id, command);

        if (sessions.phase(id) == SessionPhase.CLOSED) {
            LOG.log(SessionLogEvent.LOGIN_IGNORED, reason("SESSION_CLOSED"));
            return Optional.empty();
        }

        if (result instanceof LoginSucceeded success) {
            var principal = success.principal();
            var catalog = new CharacterCatalogMapper().map(characters.execute(principal.accountId()));

            if (sessions.phase(id) == SessionPhase.CLOSED) {
                LOG.log(SessionLogEvent.LOGIN_IGNORED, reason("SESSION_CLOSED"));
                return Optional.empty();
            }

            LOG.log(
                SessionLogEvent.LOGIN_SUCCEEDED,
                accountId(principal.accountId().value()),
                outcome("SUCCEEDED"),
                durationMs(elapsed(started))
            );
            return Optional.of(
                new PrUserLogin(true, principal.accountId().value(), wireGender(principal.gender()), 0, catalog)
            );
        }

        if (result instanceof LoginRejected rejection) {
            int code = switch (rejection.reason()) {
                case ACCOUNT_NOT_FOUND -> 1;
                case WRONG_CREDENTIAL -> 7;
                case ACCOUNT_IN_USE -> 2;
            };
            return rejected(code, rejection.reason().name(), started);
        }

        LOG.log(SessionLogEvent.LOGIN_IGNORED, reason("SESSION_NOT_ANONYMOUS"));
        return Optional.empty();
    }

    public void close(String closeReason) {
        sessions.close(id).ifPresent(
            principal -> LOG.log(
                SessionLogEvent.ENDED,
                connectionId(connection),
                sessionId(id.value()),
                accountId(principal.accountId().value()),
                reason(closeReason)
            )
        );
    }

    private Optional<PrUserLogin> rejected(int code, String rejection, long started) {
        LOG.log(SessionLogEvent.LOGIN_REJECTED, reason(rejection), durationMs(elapsed(started)));
        return Optional.of(new PrUserLogin(false, 0, 0, code));
    }

    private int wireGender(AccountGender gender) {
        return switch (gender) {
            case MALE -> 1;
            case FEMALE -> 2;
        };
    }

    private long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
