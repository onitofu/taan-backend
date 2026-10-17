package taan.online.account.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.outcome;
import static taan.online.logging.LogField.reason;

import taan.online.account.logging.AccountLogEvent;
import taan.online.account.repository.AccountRepository;
import taan.online.account.service.model.RegisterAccountCommand;
import taan.online.account.service.model.RegistrationResult;
import taan.online.logging.EventLogger;

public final class RegisterAccountAction {
    private static final EventLogger LOG = EventLogger.forClass(RegisterAccountAction.class);

    private final AccountRepository accounts;
    private final CredentialHasher credentials;

    public RegisterAccountAction(AccountRepository accounts, CredentialHasher credentials) {
        this.accounts = accounts;
        this.credentials = credentials;
    }

    public RegistrationResult execute(RegisterAccountCommand command) {
        if (accounts.existsByLogin(command.login())) {
            return alreadyExists();
        }

        var account = accounts.tryCreate(command.login(), credentials.hash(command.credential()), command.gender());

        if (account.isEmpty()) {
            return alreadyExists();
        }

        LOG.log(
            AccountLogEvent.REGISTRATION_SUCCEEDED,
            accountId(account.orElseThrow().id().value()),
            outcome("CREATED")
        );
        return RegistrationResult.CREATED;
    }

    private RegistrationResult alreadyExists() {
        LOG.log(AccountLogEvent.REGISTRATION_REJECTED, reason("ALREADY_EXISTS"));
        return RegistrationResult.ALREADY_EXISTS;
    }
}
