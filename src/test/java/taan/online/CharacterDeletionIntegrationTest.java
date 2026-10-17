package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;

class CharacterDeletionIntegrationTest {
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
    void archivesTheLastCharacterAndAllowsNameReuseAcrossReconnectAndRestart() throws Exception {
        var owner = database.account("owner@example.test", AccountGender.FEMALE);
        var original = database.create(owner.id(), "Luma", AccountGender.FEMALE).catalog().getFirst();
        var config = database.configuration();
        long id = original.id().value();

        try (var application = new Application(config)) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                assertEquals(1, CharacterTestClient.catalog(client.login(owner.login().value()), 37).size());

                client.sendFragmented(CharacterFixtures.delete(id, 0));
                byte[] deleted = client.read();

                assertArrayEquals(CharacterFixtures.root("event-0047-reply.frame.hex"), deleted);

                client.sendTogether(CharacterFixtures.delete(id, 0), CharacterFixtures.confirm("Luma", 0));

                assertArrayEquals(CharacterFixtures.root("event-0035-reply.frame.hex"), client.read());
                assertEquals(0, client.read()[4]);

                client.disconnect();
            }

            try (var client = new CharacterTestClient(config.port())) {
                assertTrue(CharacterTestClient.catalog(client.login(owner.login().value()), 37).isEmpty());

                client.send(CharacterFixtures.create("Luma", AccountGender.FEMALE, 0));
                byte[] created = client.read();

                assertEquals(0, created[4]);
                assertTrue(CharacterTestClient.catalog(created, 7).getFirst().id() > id);

                client.disconnect();
            }
        }

        try (var application = new Application(config)) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                var entries = CharacterTestClient.catalog(client.login(owner.login().value()), 37);

                assertEquals(1, entries.size());
                assertTrue(entries.getFirst().id() > id);
                assertEquals("Luma", entries.getFirst().name());
                assertArrayEquals(
                    CharacterTestClient.appearanceBytes(CharacterFixtures.create("Luma", AccountGender.FEMALE, 0)),
                    entries.getFirst().parts()
                );

                client.disconnect();
            }
        }

        assertEquals(1, database.scalar("SELECT count(*) FROM characters WHERE deleted_at IS NOT NULL"));
        assertEquals(32, database.scalar("SELECT count(*) FROM character_appearance_parts"));
    }

    @Test
    void returnsTheEntireRemainingCatalogAndRejectsForeignIdsWithoutReplacingIt() throws Exception {
        var owner = database.account("owner@example.test", AccountGender.MALE);
        var stranger = database.account("stranger@example.test", AccountGender.FEMALE);
        long foreign = database.seed(stranger.id(), "Luma", CharacterFixtures.appearance(AccountGender.FEMALE));

        for (int index = 0; index < 10; index++) {
            database.seed(owner.id(), "Hero" + index, CharacterFixtures.appearance(AccountGender.MALE));
        }

        var config = database.configuration();

        try (var application = new Application(config)) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                var before = CharacterTestClient.catalog(client.login(owner.login().value()), 37);
                client.sendTogether(CharacterFixtures.delete(foreign, 0), CharacterFixtures.delete(0, 0));

                assertArrayEquals(CharacterFixtures.root("event-0035-reply.frame.hex"), client.read());
                assertArrayEquals(CharacterFixtures.root("event-0035-reply.frame.hex"), client.read());

                client.send(CharacterFixtures.delete(before.get(4).id(), 0));
                byte[] reply = client.read();
                var remaining = CharacterTestClient.catalog(reply, 7);

                assertEquals(0, reply[4]);
                assertEquals(9, remaining.size());
                assertEquals(
                    before.stream().filter(entry -> entry.id() != before.get(4).id()).map(
                        CharacterCatalogEntry::id
                    ).toList(),
                    remaining.stream().map(CharacterCatalogEntry::id).toList()
                );

                for (var entry : remaining) {
                    assertArrayEquals(before.getFirst().parts(), entry.parts());
                }

                client.send(CharacterFixtures.create("OtherHero", AccountGender.MALE, 0));

                assertEquals(2, client.read()[4]);

                client.disconnect();
            }
        }

        assertEquals(1, database.characters().findByAccount(stranger.id()).size());
        assertEquals(9, database.characters().findByAccount(owner.id()).size());
    }

    @Test
    void closesWrongPhaseUnsupportedSlotsAndMalformedRequestsWithoutArchiving() throws Exception {
        var owner = database.account("owner@example.test", AccountGender.MALE);
        long id = database.seed(owner.id(), "MaleHero", CharacterFixtures.appearance(AccountGender.MALE));
        var config = database.configuration();

        try (var application = new Application(config)) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                client.send(CharacterFixtures.delete(id, 0));
                client.awaitClosure();
            }

            for (byte[] root : new byte[][]{
                CharacterFixtures.delete(id, 1),
                Arrays.copyOf(CharacterFixtures.delete(id, 0), 8)
            }) {
                try (var client = new CharacterTestClient(config.port())) {
                    client.login(owner.login().value());
                    client.send(root);
                    client.awaitClosure();
                }
            }
        }

        assertEquals(0, database.scalar("SELECT count(*) FROM characters WHERE deleted_at IS NOT NULL"));
        assertEquals(1, database.characters().findByAccount(owner.id()).size());
    }
}
