package taan.online.account.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import taan.online.TestFixtures;
import taan.online.account.service.model.ClientCredential;
import taan.online.account.service.model.RegisterAccountCommand;

class AccountTest {
    @Test
    void preservesIdentityAndRegistrationDataWithoutLeakingSecrets() {
        var login = new LoginName("AUTH-Fixture@Example.Test");
        var credential = new ClientCredential(TestFixtures.CREDENTIAL);
        var verifier = new CredentialVerifier(TestFixtures.VERIFIER);
        var gender = AccountGender.FEMALE;
        var account = new Account(new AccountId(0xffff_ffffL), login, verifier, gender, Instant.EPOCH);
        var command = new RegisterAccountCommand(login, credential, gender);

        assertEquals(TestFixtures.LOGIN, account.login().value());
        assertEquals(0xffff_ffffL, account.id().value());
        assertEquals(Instant.EPOCH, account.createdAt());
        assertEquals(TestFixtures.VERIFIER, account.credentialVerifier().encoded());
        assertEquals(AccountGender.FEMALE, account.gender());
        assertEquals(credential, command.credential());
        assertEquals(login, command.login());
        assertEquals(gender, command.gender());
        assertEquals("x".repeat(50), new LoginName("X".repeat(50)).value());

        for (Object sensitive : new Object[]{login, credential, verifier, account, command}) {
            assertFalse(sensitive.toString().contains(TestFixtures.LOGIN));
            assertFalse(sensitive.toString().contains(TestFixtures.CREDENTIAL));
            assertFalse(sensitive.toString().contains(TestFixtures.VERIFIER));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " with-space", "with-space ", "with space", "a\nb", "é@example.test", "\u007f"})
    void rejectsUnsupportedLoginWithoutEchoingInput(String input) {
        var failure = assertThrows(IllegalArgumentException.class, () -> new LoginName(input));

        assertEquals("Unsupported login format", failure.getMessage());
    }

    @Test
    void rejectsInvalidIdentityLoginAndCredential() {
        assertThrows(IllegalArgumentException.class, () -> new AccountId(0));
        assertThrows(IllegalArgumentException.class, () -> new AccountId(0x1_0000_0000L));
        assertEquals(1, new AccountId(1).value());
        assertThrows(IllegalArgumentException.class, () -> new LoginName("a".repeat(51)));
        assertThrows(IllegalArgumentException.class, () -> new ClientCredential("A".repeat(32)));
        assertThrows(IllegalArgumentException.class, () -> new ClientCredential("0".repeat(31)));
        assertThrows(IllegalArgumentException.class, () -> new CredentialVerifier("secret"));
        assertThrows(NullPointerException.class, () -> new ClientCredential(null));
        assertThrows(NullPointerException.class, () -> new LoginName(null));
        assertThrows(NullPointerException.class, () -> new CredentialVerifier(null));
    }
}
