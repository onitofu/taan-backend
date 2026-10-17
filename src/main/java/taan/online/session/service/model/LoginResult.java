package taan.online.session.service.model;

public sealed interface LoginResult permits LoginSucceeded, LoginRejected, LoginIgnored {
}
