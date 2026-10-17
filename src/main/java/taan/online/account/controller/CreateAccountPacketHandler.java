package taan.online.account.controller;

import static taan.online.logging.LogField.reason;

import taan.online.account.logging.AccountLogEvent;
import taan.online.account.model.AccountGender;
import taan.online.account.model.LoginName;
import taan.online.account.protocol.packet.PqCreateAccount;
import taan.online.account.protocol.packet.PrCreateAccount;
import taan.online.account.service.RegisterAccountAction;
import taan.online.account.service.model.ClientCredential;
import taan.online.account.service.model.RegisterAccountCommand;
import taan.online.account.service.model.RegistrationResult;
import taan.online.logging.EventLogger;

public final class CreateAccountPacketHandler {
    private static final EventLogger LOG = EventLogger.forClass(CreateAccountPacketHandler.class);

    private final RegisterAccountAction action;

    public CreateAccountPacketHandler(RegisterAccountAction action) {
        this.action = action;
    }

    public PrCreateAccount handle(PqCreateAccount request) {
        RegisterAccountCommand command;

        try {
            if (request.selector() != 0) {
                return rejected(request.selector());
            }

            command = new RegisterAccountCommand(
                new LoginName(request.login()),
                new ClientCredential(request.credential()),
                gender(request.gender())
            );
        } catch (IllegalArgumentException failure) {
            return rejected(request.selector());
        }

        RegistrationResult result = action.execute(command);
        return new PrCreateAccount(status(result), request.selector());
    }

    private AccountGender gender(int value) {
        return switch (value) {
            case 1 -> AccountGender.MALE;
            case 2 -> AccountGender.FEMALE;
            default -> throw new IllegalArgumentException("Unsupported account gender");
        };
    }

    private static int status(RegistrationResult result) {
        return switch (result) {
            case CREATED -> 0;
            case ALREADY_EXISTS -> 1;
        };
    }

    private PrCreateAccount rejected(int selector) {
        LOG.log(AccountLogEvent.REGISTRATION_REJECTED, reason("INVALID_INPUT"));
        return new PrCreateAccount(255, selector);
    }
}
