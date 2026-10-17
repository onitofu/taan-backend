package taan.online.session.service.model;

import taan.online.session.model.AuthenticatedPrincipal;

public record LoginSucceeded(AuthenticatedPrincipal principal) implements LoginResult {
}
