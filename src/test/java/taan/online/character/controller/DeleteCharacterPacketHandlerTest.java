package taan.online.character.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.TestLevels;
import taan.online.account.model.AccountGender;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.character.service.DeleteCharacterAction;
import taan.online.character.service.TestAccountAccess;
import taan.online.protocol.MalformedPacketException;

class DeleteCharacterPacketHandlerTest {
    @Test
    void mapsSuccessfulAndRepeatedDeletionAndRejectsUnsupportedLocalAccounts() {
        var access = new TestAccountAccess();
        var repository = new TestCharacterRepository();
        repository.stored.add(CharacterFixtures.character(10, access.owner, AccountGender.FEMALE, "Luma"));
        var handler = handler(access, repository);
        var session = UUID.randomUUID();
        var reply = handler.handle(session, CharacterFixtures.delete(10, 0)).orElseThrow();

        assertEquals(0, reply.result());
        assertEquals(0, reply.localSlot());
        assertTrue(reply.catalog().info().isEmpty());
        assertTrue(reply.catalog().appearances().isEmpty());

        var repeat = handler.handle(session, CharacterFixtures.delete(10, 0)).orElseThrow();

        assertEquals(1, repeat.result());
        assertNull(repeat.catalog());
        assertThrows(
            MalformedPacketException.class,
            () -> handler.handle(session, CharacterFixtures.delete(10, 1))
        );
        assertEquals(2, repository.transactions);
    }

    @Test
    void skipsClosedRequestsAndDropsLateResultsWithoutUndoingCommittedArchival() {
        var closed = new TestAccountAccess();
        closed.active = false;
        var untouched = new TestCharacterRepository();

        assertTrue(handler(closed, untouched).handle(UUID.randomUUID(), new byte[0]).isEmpty());
        assertEquals(0, untouched.transactions);

        for (int cutoff : new int[]{2, 3}) {
            var access = new TestAccountAccess();
            var repository = new TestCharacterRepository();
            repository.stored.add(CharacterFixtures.character(10, access.owner, AccountGender.FEMALE, "Luma"));
            access.onAccess = () -> {
                if (access.calls == cutoff) {
                    access.active = false;
                }
            };

            assertTrue(
                handler(access, repository).handle(UUID.randomUUID(), CharacterFixtures.delete(10, 0)).isEmpty()
            );
            assertEquals(cutoff == 3 ? 1 : 0, repository.commits);
            assertEquals(cutoff == 3 ? 1 : 0, repository.archived.size());
        }
    }

    private DeleteCharacterPacketHandler handler(TestAccountAccess access, TestCharacterRepository repository) {
        return new DeleteCharacterPacketHandler(
            new DeleteCharacterAction(
                access,
                repository
            ),
            access,
            TestLevels.table()
        );
    }
}
