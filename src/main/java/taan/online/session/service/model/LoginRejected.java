package taan.online.session.service.model;

public record LoginRejected(LoginRejection reason) implements LoginResult {
}
