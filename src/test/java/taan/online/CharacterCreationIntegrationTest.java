package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.character.model.AppearancePart;
import taan.online.character.model.CharacterAppearance;

class CharacterCreationIntegrationTest {
    private static CharacterTestDatabase database;

    @BeforeAll
    static void start() {
        database = new CharacterTestDatabase();
    }

    @AfterAll
    static void stop() {
        if (database != null) {
            database.close();
        }
    }

    @BeforeEach
    void reset() throws Exception {
        database.reset();
    }

    @Test
    void retainsBothNondefaultAppearancesAcrossReconnectAndProcessRestart() throws Exception {
        for (var gender : AccountGender.values()) {
            String login = gender == AccountGender.MALE ? "male@example.test" : "female@example.test";
            String name = gender == AccountGender.MALE ? "MaleHero" : "Luma";
            var owner = database.account(login, gender);
            var config = database.configuration();
            byte[] creation;

            try (var application = new Application(config)) {
                application.start();

                try (var client = new CharacterTestClient(config.port())) {
                    assertTrue(CharacterTestClient.catalog(client.login(login), 37).isEmpty());

                    client.sendTogether(
                        CharacterFixtures.confirm("FirstHero", 0),
                        CharacterFixtures.create(name, gender, 0)
                    );

                    assertEquals(0, client.read()[4]);

                    creation = client.read();

                    assertEquals(0, creation[4]);

                    var entries = CharacterTestClient.catalog(creation, 7);

                    assertEquals(1, entries.size());
                    assertEquals(name, entries.getFirst().name());
                    assertEquals(gender == AccountGender.MALE ? 1 : 2, entries.getFirst().gender());
                    assertEquals(entries.getFirst().gender(), entries.getFirst().mode());
                    assertArrayEquals(
                        CharacterTestClient.appearanceBytes(CharacterFixtures.create(name, gender, 0)),
                        entries.getFirst().parts()
                    );

                    client.sendFragmented(CharacterFixtures.create("OtherHero", gender, 0));

                    assertEquals(2, client.read()[4]);

                    client.disconnect();
                }

                try (var reconnected = new CharacterTestClient(config.port())) {
                    assertSameCatalog(creation, reconnected.login(login));

                    reconnected.disconnect();
                }
            }

            try (var application = new Application(config)) {
                application.start();

                try (var reconnected = new CharacterTestClient(config.port())) {
                    assertSameCatalog(creation, reconnected.login(login));

                    reconnected.disconnect();
                }
            }

            var stored = database.characters().findByAccount(owner.id());

            assertEquals(1, stored.size());
            assertEquals(CharacterFixtures.appearance(gender), stored.getFirst().appearance());
        }
    }

    @Test
    void returnsAllTenAdministrativeCharactersWithMatchingOrderAndAppearance() throws Exception {
        var owner = database.account("admin-test@example.test", AccountGender.FEMALE);
        var originals = new ArrayList<byte[]>();
        var ids = new ArrayList<Long>();

        for (int index = 0; index < 10; index++) {
            var parts = new ArrayList<>(CharacterFixtures.appearance(AccountGender.FEMALE).parts());

            if (index % 2 == 1) {
                parts.set(1, new AppearancePart(1, 731, 300, 300, 300));
            }

            var appearance = new CharacterAppearance(parts);
            ids.add(database.seed(owner.id(), "SeedHero" + index, appearance));
            originals.add(
                CharacterTestClient.appearanceBytes(CharacterFixtures.create("SeedHero" + index, appearance, 0))
            );
        }

        var config = database.configuration();

        try (var application = new Application(config)) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                var entries = CharacterTestClient.catalog(client.login(owner.login().value()), 37);

                assertEquals(10, entries.size());

                for (int index = 0; index < 10; index++) {
                    assertEquals(ids.get(index).longValue(), entries.get(index).id());
                    assertEquals("SeedHero" + index, entries.get(index).name());
                    assertArrayEquals(originals.get(index), entries.get(index).parts());
                }

                client.send(CharacterFixtures.create("Luma", AccountGender.FEMALE, 0));

                assertEquals(2, client.read()[4]);

                client.disconnect();
            }
        }

        assertEquals(10, database.characters().findByAccount(owner.id()).size());
    }

    @Test
    void filtersNamesBeforeInsertionAndAllowsDisplayNamesThatDifferOnlyByCase() throws Exception {
        var first = database.account("first@example.test", AccountGender.FEMALE);
        var second = database.account("second@example.test", AccountGender.FEMALE);
        var config = database.configuration();

        try (var application = new Application(config)) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                client.login(first.login().value());

                for (String name : new String[]{"ADMIN", "AdMiN", "abc", "A😀z"}) {
                    client.send(CharacterFixtures.confirm(name, 0));

                    assertEquals(1, client.read()[4]);

                    client.send(CharacterFixtures.create(name, AccountGender.FEMALE, 0));

                    assertEquals(2, client.read()[4]);
                    assertEquals(0, database.scalar("SELECT count(*) FROM characters"));
                }

                client.sendFragmented(CharacterFixtures.create("Luma", AccountGender.FEMALE, 0));

                assertEquals(0, client.read()[4]);

                client.disconnect();
            }

            try (var client = new CharacterTestClient(config.port())) {
                client.login(second.login().value());
                client.send(CharacterFixtures.create("Luma", AccountGender.FEMALE, 0));

                assertEquals(1, client.read()[4]);

                client.send(CharacterFixtures.create("luma", AccountGender.FEMALE, 0));

                assertEquals(0, client.read()[4]);

                client.disconnect();
            }
        }

        assertEquals(2, database.scalar("SELECT count(*) FROM characters"));
    }

    @Test
    void closesInvalidPhasesLocalSlotsAndTruncatedPacketsWithoutCreatingCharacters() throws Exception {
        var owner = database.account("protocol@example.test", AccountGender.MALE);
        var config = database.configuration();

        try (var application = new Application(config)) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                client.send(CharacterFixtures.confirm("MaleHero", 0));
                client.awaitClosure();
            }

            try (var client = new CharacterTestClient(config.port())) {
                client.login(owner.login().value());
                client.send(CharacterFixtures.create("MaleHero", AccountGender.MALE, 1));
                client.awaitClosure();
            }

            try (var client = new CharacterTestClient(config.port())) {
                client.login(owner.login().value());
                byte[] root = CharacterFixtures.create("MaleHero", AccountGender.MALE, 0);
                client.send(Arrays.copyOf(root, root.length - 1));
                client.awaitClosure();
            }
        }

        assertEquals(0, database.scalar("SELECT count(*) FROM characters"));
    }

    private void assertSameCatalog(byte[] creation, byte[] login) {
        assertArrayEquals(
            Arrays.copyOfRange(creation, 7, creation.length),
            Arrays.copyOfRange(login, 37, login.length - 7)
        );
    }
}
