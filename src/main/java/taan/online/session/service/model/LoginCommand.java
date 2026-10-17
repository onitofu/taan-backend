package taan.online.session.service.model;

import java.util.Objects;
import taan.online.account.model.LoginName;
import taan.online.account.service.model.ClientCredential;

public record LoginCommand(LoginName login, ClientCredential credential) {
    public LoginCommand {
        Objects.requireNonNull(login);
        Objects.requireNonNull(credential);
    }

    @Override
    public String toString() {
        return "LoginCommand[REDACTED]";
    }
}
