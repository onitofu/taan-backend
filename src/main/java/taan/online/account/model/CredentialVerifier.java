package taan.online.account.model;

import java.util.Objects;

public record CredentialVerifier(String encoded) {
    public CredentialVerifier {
        Objects.requireNonNull(encoded, "Verifier is required");

        if (!encoded.startsWith("$argon2id$v=19$")) {
            throw new IllegalArgumentException("Unsupported verifier encoding");
        }
    }

    @Override
    public String toString() {
        return "CredentialVerifier[REDACTED]";
    }
}
