package taan.online.match.service;

import taan.online.match.model.MatchParticipant;
import taan.online.match.service.model.MatchCommandContext;

public interface MatchAdmissionAccess {
    boolean current(MatchCommandContext context, MatchParticipant participant);
}
