package taan.online.character.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.account.model.AccountGender;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.character.service.ConfirmNameAction;
import taan.online.character.service.CreateCharacterAction;
import taan.online.character.service.TestAccountAccess;
import taan.online.protocol.MalformedPacketException;

class CharacterPacketHandlerTest {
    @Test
    void mapsAvailabilityFilteringAndCreationOutcomesWithoutAddingWireStatuses() {
        var access = new TestAccountAccess();
        var repository = new TestCharacterRepository();
        var confirm = confirm(access, repository);
        var create = create(access, repository);
        var id = UUID.randomUUID();

        assertEquals(0, confirm.handle(id, CharacterFixtures.confirm("Luma", 0)).orElseThrow().result());
        assertEquals(1, confirm.handle(id, CharacterFixtures.confirm("ADMIN", 0)).orElseThrow().result());
        assertEquals(
            2,
            create.handle(id, CharacterFixtures.create("ADMIN", AccountGender.FEMALE, 0)).orElseThrow().result()
        );

        repository.conflict = true;

        assertEquals(
            1,
            create.handle(id, CharacterFixtures.create("Luma", AccountGender.FEMALE, 0)).orElseThrow().result()
        );

        repository.conflict = false;
        var reply = create.handle(id, CharacterFixtures.create("Luma", AccountGender.FEMALE, 0)).orElseThrow();

        assertEquals(0, reply.result());
        assertEquals(0, reply.localSlot());
        assertEquals("Luma", reply.catalog().info().getFirst().name());
        assertEquals(1, confirm.handle(id, CharacterFixtures.confirm("Luma", 0)).orElseThrow().result());
        assertEquals(
            2,
            create.handle(id, CharacterFixtures.create("OtherHero", AccountGender.FEMALE, 0)).orElseThrow().result()
        );
    }

    @Test
    void rejectsUnsupportedLocalAccountsBeforeDatabaseWork() {
        var access = new TestAccountAccess();
        var repository = new TestCharacterRepository();
        var id = UUID.randomUUID();

        assertThrows(
            MalformedPacketException.class,
            () -> confirm(access, repository).handle(id, CharacterFixtures.confirm("Luma", 1))
        );
        assertThrows(
            MalformedPacketException.class,
            () -> create(access, repository).handle(id, CharacterFixtures.create("Luma", AccountGender.FEMALE, 1))
        );
        assertEquals(0, repository.checks);
        assertEquals(0, repository.transactions);
    }

    @Test
    void dropsClosedRequestsBeforeDecodingAndLateCheckResultsWithoutResponses() {
        var access = new TestAccountAccess();
        var repository = new TestCharacterRepository();
        var id = UUID.randomUUID();
        access.active = false;

        assertTrue(confirm(access, repository).handle(id, new byte[0]).isEmpty());
        assertTrue(create(access, repository).handle(id, new byte[0]).isEmpty());
        assertEquals(0, repository.transactions);
        assertEquals(0, repository.checks);

        for (int cutoff : new int[]{2, 3}) {
            var changing = new TestAccountAccess();
            changing.onAccess = () -> {
                if (changing.calls == cutoff) {
                    changing.active = false;
                }
            };

            assertTrue(confirm(changing, repository).handle(id, CharacterFixtures.confirm("Luma", 0)).isEmpty());
        }

        assertEquals(1, repository.checks);
    }

    @Test
    void dropsCreationResultsForClosedSessionsWhilePreservingAnAlreadyCommittedCharacter() {
        for (int cutoff : new int[]{2, 3}) {
            var access = new TestAccountAccess();
            var repository = new TestCharacterRepository();
            access.onAccess = () -> {
                if (access.calls == cutoff) {
                    access.active = false;
                }
            };

            assertTrue(
                create(access, repository).handle(
                    UUID.randomUUID(),
                    CharacterFixtures.create("Luma", AccountGender.FEMALE, 0)
                ).isEmpty()
            );
            assertEquals(cutoff == 3 ? 1 : 0, repository.commits);
            assertEquals(cutoff == 3 ? 1 : 0, repository.stored.size());
        }
    }

    private ConfirmNamePacketHandler confirm(TestAccountAccess access, TestCharacterRepository repository) {
        return new ConfirmNamePacketHandler(
            new ConfirmNameAction(access, repository, CharacterFixtures.filter()),
            access
        );
    }

    private CreateCharacterPacketHandler create(TestAccountAccess access, TestCharacterRepository repository) {
        return new CreateCharacterPacketHandler(
            new CreateCharacterAction(access, repository, CharacterFixtures.filter(), CharacterFixtures.options()),
            access
        );
    }
}
