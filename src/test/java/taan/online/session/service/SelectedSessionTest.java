package taan.online.session.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.SelectionFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.protocol.MalformedPacketException;
import taan.online.session.controller.CashRefreshPacketHandler;
import taan.online.session.controller.ClientLoggingPacketHandler;
import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.session.model.SelectedCharacterProfile;
import taan.online.session.model.SessionId;
import taan.online.session.model.SessionPhase;

class SelectedSessionTest {
    @Test
    void ownsSelectionAndClearsOnlyTheClosingSessionWithoutExposingMutableState() {
        var registry = new SessionRegistry();
        var missing = new SessionId(UUID.randomUUID());
        var anonymous = registry.open();
        var character = CharacterFixtures.character(1001, new AccountId(1), AccountGender.FEMALE, "Luma");
        var principal = new AuthenticatedPrincipal(new AccountId(1), AccountGender.MALE);

        assertFalse(registry.trySelect(missing, character));
        assertFalse(registry.trySelect(anonymous, character));
        assertTrue(registry.selectedCharacter(missing).isEmpty());
        assertTrue(registry.principal(missing).isEmpty());
        assertTrue(registry.selectablePrincipal(missing).isEmpty());
        assertTrue(registry.selectablePrincipal(anonymous).isEmpty());
        assertThrows(NullPointerException.class, () -> registry.trySelect(anonymous, null));

        registry.tryClaim(anonymous, principal);

        assertEquals(principal, registry.principal(anonymous).orElseThrow());
        var access = new SessionCharacterAccess(registry);
        var channels = new SessionChannelAccess(registry);
        var inventoryAccess = new SessionInventoryAccess(registry);

        assertTrue(inventoryAccess.selected(missing.value()).isEmpty());

        assertFalse(channels.hasSelectedCharacter(anonymous.value()));
        assertFalse(
            registry.trySelect(
                anonymous,
                CharacterFixtures.character(
                    9,
                    new AccountId(2),
                    AccountGender.MALE,
                    "MaleHero"
                )
            )
        );
        assertTrue(registry.channelParticipant(missing).isEmpty());
        assertTrue(access.trySelect(anonymous.value(), character));
        assertEquals(SessionPhase.CHARACTER_SELECTED, registry.phase(anonymous));
        assertEquals(character.id().value(), inventoryAccess.selected(anonymous.value()).orElseThrow().characterId());
        assertTrue(channels.hasSelectedCharacter(anonymous.value()));
        assertTrue(access.authenticatedAccount(anonymous.value()).isEmpty());
        assertEquals(character.id(), access.selectedCharacter(anonymous.value()).orElseThrow());
        assertEquals(AccountGender.FEMALE, channels.selectedCharacter(anonymous.value()).orElseThrow().gender());
        assertThrows(
            NullPointerException.class,
            () -> new SelectedCharacterProfile(character.id(), character.name(), character.level(), null)
        );
        assertFalse(
            registry.trySelect(
                anonymous,
                CharacterFixtures.character(
                    1002,
                    new AccountId(1),
                    AccountGender.FEMALE,
                    "Luma"
                )
            )
        );

        registry.close(anonymous);
        var reconnected = registry.open();
        registry.tryClaim(reconnected, principal);

        assertTrue(
            registry.trySelect(
                reconnected,
                CharacterFixtures.character(
                    1002,
                    new AccountId(1),
                    AccountGender.FEMALE,
                    "Luma"
                )
            )
        );
        assertTrue(registry.close(anonymous).isEmpty());
        assertEquals(1002, registry.selectedCharacter(reconnected).orElseThrow().value());
        assertFalse(channels.hasSelectedCharacter(anonymous.value()));
    }

    @Test
    void ignoresClosedBackgroundRequestsAndRejectsUntrustedSlotsAndCharacterIds() {
        var registry = new SessionRegistry();
        var id = registry.open();
        var characters = new TestCharacterRepository();
        characters.stored.add(CharacterFixtures.character(1001, new AccountId(1), AccountGender.MALE, "MaleHero"));
        var cash = new CashRefreshPacketHandler(registry, new RefreshBalancesAction(registry, characters));
        var logging = new ClientLoggingPacketHandler(registry);

        assertTrue(cash.handle(id.value(), new byte[0]).isEmpty());
        assertFalse(logging.handle(id.value(), new byte[0]));

        registry.tryClaim(id, new AuthenticatedPrincipal(new AccountId(1), AccountGender.MALE));
        registry.trySelect(id, CharacterFixtures.character(1001, new AccountId(1), AccountGender.MALE, "MaleHero"));
        var response = cash.handle(id.value(), SelectionFixtures.cash(123456, 0)).orElseThrow();

        assertEquals(0, response.cash().balance());
        assertEquals(0, response.cash().localSlot());
        assertTrue(logging.handle(id.value(), SelectionFixtures.logging(1001)));
        assertThrows(MalformedPacketException.class, () -> cash.handle(id.value(), SelectionFixtures.cash(0, 1)));
        assertThrows(MalformedPacketException.class, () -> logging.handle(id.value(), SelectionFixtures.logging(1002)));

        registry.close(id);

        assertTrue(cash.handle(id.value(), new byte[0]).isEmpty());
        assertFalse(logging.handle(id.value(), new byte[0]));
    }
}
