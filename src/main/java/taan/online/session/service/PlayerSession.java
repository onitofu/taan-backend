package taan.online.session.service;

import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.character.model.CharacterId;
import taan.online.session.model.SessionId;
import taan.online.session.model.SessionPhase;

final class PlayerSession {
    final SessionId id;
    SessionPhase phase = SessionPhase.ANONYMOUS;
    AuthenticatedPrincipal principal;
    CharacterId selectedCharacterId;

    PlayerSession(SessionId id) {
        this.id = id;
    }
}
