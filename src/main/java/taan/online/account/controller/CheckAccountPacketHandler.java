package taan.online.account.controller;

import static taan.online.logging.LogField.outcome;

import taan.online.account.logging.AccountLogEvent;
import taan.online.account.model.LoginName;
import taan.online.account.protocol.packet.PqCheckAccount;
import taan.online.account.protocol.packet.PrCheckAccount;
import taan.online.account.service.CheckAccountAction;
import taan.online.account.service.model.CheckAccountResult;
import taan.online.logging.EventLogger;

public final class CheckAccountPacketHandler {
    private static final EventLogger LOG = EventLogger.forClass(CheckAccountPacketHandler.class);

    private final CheckAccountAction action;

    public CheckAccountPacketHandler(CheckAccountAction action) {
        this.action = action;
    }

    public PrCheckAccount handle(PqCheckAccount request) {
        CheckAccountResult result = check(request);

        LOG.log(AccountLogEvent.CHECK_COMPLETED, outcome(result.name()));

        int status = switch (result) {
            case EXISTS -> 0;
            case NOT_FOUND -> 1;
            case REJECTED -> 255;
        };
        return new PrCheckAccount(status, request.selector());
    }

    private CheckAccountResult check(PqCheckAccount request) {
        if (request.selector() != 0) {
            return CheckAccountResult.REJECTED;
        }

        LoginName login;

        try {
            login = new LoginName(request.login());
        } catch (IllegalArgumentException failure) {
            return CheckAccountResult.REJECTED;
        }

        return action.execute(login);
    }
}
