package taan.online.account.service.model;

import java.util.Objects;
import taan.online.account.model.AccountGender;
import taan.online.account.model.LoginName;

public record RegisterAccountCommand(LoginName login, ClientCredential credential, AccountGender gender) {
    public RegisterAccountCommand {
        Objects.requireNonNull(login, "Login is required");
        Objects.requireNonNull(credential, "Credential is required");
        Objects.requireNonNull(gender, "Gender is required");
    }

    @Override
    public String toString() {
        return "RegisterAccountCommand[REDACTED]";
    }
}
