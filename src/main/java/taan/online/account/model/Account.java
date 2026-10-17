package taan.online.account.model;

import java.time.Instant;
import java.util.Objects;

public record Account(
    AccountId id,
    LoginName login,
    CredentialVerifier credentialVerifier,
    AccountGender gender,
    Instant createdAt
) {
    public Account {
        Objects.requireNonNull(id, "Account identity is required");
        Objects.requireNonNull(login, "Login is required");
        Objects.requireNonNull(credentialVerifier, "Verifier is required");
        Objects.requireNonNull(gender, "Gender is required");
        Objects.requireNonNull(createdAt, "Creation time is required");
    }

    @Override
    public String toString() {
        return "Account[id=" + id.value() + "]";
    }
}
