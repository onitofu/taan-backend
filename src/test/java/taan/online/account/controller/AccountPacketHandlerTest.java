package taan.online.account.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import taan.online.TestFixtures;
import taan.online.TestCredentialHasher;
import taan.online.account.model.AccountGender;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.protocol.packet.PqCheckAccount;
import taan.online.account.protocol.packet.PqCreateAccount;
import taan.online.account.service.CheckAccountAction;
import taan.online.account.service.RegisterAccountAction;

class AccountPacketHandlerTest {
    @Test
    void mapsAccountChecksAndRegistrationOutcomes() {
        var repository = new PacketHandlerTestAccountRepository();
        var check = new CheckAccountPacketHandler(new CheckAccountAction(repository));
        var create = new CreateAccountPacketHandler(
            new RegisterAccountAction(
                repository,
                new TestCredentialHasher(credential -> new CredentialVerifier(TestFixtures.VERIFIER))
            )
        );

        assertEquals(1, check.handle(new PqCheckAccount(TestFixtures.LOGIN, 0)).status());
        assertEquals(0, create.handle(new PqCreateAccount(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 2, 0)).status());
        assertEquals(0, check.handle(new PqCheckAccount(TestFixtures.LOGIN, 0)).status());
        assertEquals(1, create.handle(new PqCreateAccount(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 0)).status());
    }

    @Test
    void mapsWireGendersAtTheProtocolBoundary() {
        for (int value : new int[]{1, 2}) {
            var repository = new PacketHandlerTestAccountRepository();
            var handler = new CreateAccountPacketHandler(
                new RegisterAccountAction(
                    repository,
                    new TestCredentialHasher(credential -> new CredentialVerifier(TestFixtures.VERIFIER))
                )
            );

            assertEquals(
                0,
                handler.handle(new PqCreateAccount(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, value, 0)).status()
            );
            assertEquals(value == 1 ? AccountGender.MALE : AccountGender.FEMALE, repository.saved().gender());
        }
    }

    @Test
    void rejectsUnsupportedProfilesAndPreservesSelectorEchoWithoutCallingPersistence() {
        var repository = new PacketHandlerTestAccountRepository();
        repository.fail(true);
        var check = new CheckAccountPacketHandler(new CheckAccountAction(repository));
        var create = new CreateAccountPacketHandler(
            new RegisterAccountAction(
                repository,
                new TestCredentialHasher(credential -> new CredentialVerifier(TestFixtures.VERIFIER))
            )
        );

        for (int selector : new int[]{1, 2, 255}) {
            var checked = check.handle(new PqCheckAccount(TestFixtures.LOGIN, selector));

            assertEquals(255, checked.status());
            assertEquals(selector, checked.selector());

            var created = create.handle(new PqCreateAccount(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, selector));

            assertEquals(255, created.status());
            assertEquals(selector, created.selector());
        }

        assertEquals(255, check.handle(new PqCheckAccount(" has-space", 0)).status());
        assertEquals(255, create.handle(new PqCreateAccount(" has-space", TestFixtures.CREDENTIAL, 1, 0)).status());
        assertEquals(255, create.handle(new PqCreateAccount(TestFixtures.LOGIN, "bad", 1, 0)).status());
        assertEquals(
            255,
            create.handle(new PqCreateAccount(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 0, 0)).status()
        );
        assertThrows(IllegalStateException.class, () -> check.handle(new PqCheckAccount(TestFixtures.LOGIN, 0)));
        assertThrows(
            IllegalStateException.class,
            () -> create.handle(new PqCreateAccount(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 0))
        );
    }
}
