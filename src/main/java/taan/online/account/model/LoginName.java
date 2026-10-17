package taan.online.account.model;

import java.util.Locale;
import java.util.Objects;

public record LoginName(String value) {
    public LoginName {
        Objects.requireNonNull(value, "Login is required");

        if (!value.matches("[!-~]{1,50}")) {
            throw new IllegalArgumentException("Unsupported login format");
        }

        value = value.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "LoginName[REDACTED]";
    }
}
