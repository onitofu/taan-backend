package taan.online.session.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.SelectionFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterId;
import taan.online.protocol.MalformedPacketException;
import taan.online.session.controller.CashRefreshPacketHandler;
import taan.online.session.controller.ClientLoggingPacketHandler;
import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.session.model.SessionId;
import taan.online.session.model.SessionPhase;

class SelectedSessionTest {
    @Test
    void ownsSelectionAndClearsOnlyTheClosingSessionWithoutExposingMutableState() {
        var registry = new SessionRegistry();
        var missing = new SessionId(UUID.randomUUID());
        var anonymous = registry.open();
        var character = new CharacterId(1001);
        var principal = new AuthenticatedPrincipal(new AccountId(1), AccountGender.FEMALE);

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

        assertFalse(channels.hasSelectedCharacter(anonymous.value()));
        assertTrue(access.trySelect(anonymous.value(), character));
        assertEquals(SessionPhase.CHARACTER_SELECTED, registry.phase(anonymous));
        assertTrue(channels.hasSelectedCharacter(anonymous.value()));
        assertTrue(access.authenticatedAccount(anonymous.value()).isEmpty());
        assertEquals(character, access.selectedCharacter(anonymous.value()).orElseThrow());
        assertFalse(registry.trySelect(anonymous, new CharacterId(1002)));

        registry.close(anonymous);
        var reconnected = registry.open();
        registry.tryClaim(reconnected, principal);

        assertTrue(registry.trySelect(reconnected, new CharacterId(1002)));
        assertTrue(registry.close(anonymous).isEmpty());
        assertEquals(1002, registry.selectedCharacter(reconnected).orElseThrow().value());
        assertFalse(channels.hasSelectedCharacter(anonymous.value()));
    }

    @Test
    void ignoresClosedBackgroundRequestsAndRejectsUntrustedSlotsAndCharacterIds() {
        var registry = new SessionRegistry();
        var id = registry.open();
        var cash = new CashRefreshPacketHandler(registry);
        var logging = new ClientLoggingPacketHandler(registry);

        assertTrue(cash.handle(id.value(), new byte[0]).isEmpty());
        assertFalse(logging.handle(id.value(), new byte[0]));

        registry.tryClaim(id, new AuthenticatedPrincipal(new AccountId(1), AccountGender.MALE));
        registry.trySelect(id, new CharacterId(1001));
        var response = cash.handle(id.value(), SelectionFixtures.cash(123456, 0)).orElseThrow();

        assertEquals(0, response.balance());
        assertEquals(0, response.localSlot());
        assertTrue(logging.handle(id.value(), SelectionFixtures.logging(1001)));
        assertThrows(MalformedPacketException.class, () -> cash.handle(id.value(), SelectionFixtures.cash(0, 1)));
        assertThrows(MalformedPacketException.class, () -> logging.handle(id.value(), SelectionFixtures.logging(1002)));

        registry.close(id);

        assertTrue(cash.handle(id.value(), new byte[0]).isEmpty());
        assertFalse(logging.handle(id.value(), new byte[0]));
    }
}
