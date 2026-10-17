package taan.online.account.security;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.service.CredentialHasher;
import taan.online.account.service.model.ClientCredential;

public final class Argon2idCredentialHasher implements CredentialHasher {
    private final Argon2Function function;

    public Argon2idCredentialHasher(int memoryKiB, int iterations, int parallelism) {
        if (memoryKiB < 19456 || iterations < 2 || parallelism < 1 || memoryKiB < 8L * parallelism) {
            throw new IllegalArgumentException("Argon2 parameters are below the required minimum");
        }

        function = Argon2Function.getInstance(memoryKiB, iterations, parallelism, 32, Argon2.ID, 19);
    }

    @Override
    public CredentialVerifier hash(ClientCredential credential) {
        return new CredentialVerifier(Password.hash(credential.hex()).addRandomSalt(16).with(function).getResult());
    }
}
