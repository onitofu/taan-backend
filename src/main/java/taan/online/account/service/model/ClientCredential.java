package taan.online.account.service.model;

import java.util.Objects;

public record ClientCredential(String hex) {
    public ClientCredential {
        Objects.requireNonNull(hex, "Credential is required");

        if (!hex.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Unsupported credential format");
        }
    }

    @Override
    public String toString() {
        return "ClientCredential[REDACTED]";
    }
}
