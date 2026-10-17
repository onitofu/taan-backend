package taan.online;

import java.util.function.Function;
import java.util.function.BiPredicate;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.service.CredentialHasher;
import taan.online.account.service.model.ClientCredential;

public final class TestCredentialHasher implements CredentialHasher {
    private final Function<ClientCredential, CredentialVerifier> hash;
    private final BiPredicate<ClientCredential, CredentialVerifier> verify;

    public TestCredentialHasher(Function<ClientCredential, CredentialVerifier> hash) {
        this(hash, (credential, verifier) -> credential.hex().equals(TestFixtures.CREDENTIAL));
    }

    public TestCredentialHasher(
        Function<ClientCredential, CredentialVerifier> hash,
        BiPredicate<ClientCredential, CredentialVerifier> verify
    ) {
        this.hash = hash;
        this.verify = verify;
    }

    @Override
    public CredentialVerifier hash(ClientCredential credential) {
        return hash.apply(credential);
    }

    @Override
    public boolean verify(ClientCredential credential, CredentialVerifier verifier) {
        return verify.test(credential, verifier);
    }
}
