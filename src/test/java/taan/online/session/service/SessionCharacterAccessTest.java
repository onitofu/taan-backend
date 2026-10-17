package taan.online.session.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.session.model.AuthenticatedPrincipal;

class SessionCharacterAccessTest {
    @Test
    void resolvesOnlyTheVerifiedIdentityOfTheExactLiveSession() {
        var sessions = new SessionRegistry();
        var access = new SessionCharacterAccess(sessions);
        var first = sessions.open();
        var owner = new AccountId(1);

        assertTrue(access.authenticatedAccount(first.value()).isEmpty());

        sessions.tryClaim(first, new AuthenticatedPrincipal(owner, AccountGender.FEMALE));

        assertEquals(owner, access.authenticatedAccount(first.value()).orElseThrow());

        sessions.close(first);
        var next = sessions.open();
        sessions.tryClaim(next, new AuthenticatedPrincipal(owner, AccountGender.FEMALE));
        sessions.close(first);

        assertTrue(access.authenticatedAccount(first.value()).isEmpty());
        assertEquals(owner, access.authenticatedAccount(next.value()).orElseThrow());
    }
}
