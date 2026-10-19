package taan.online.match.service;

final class ScenarioRejectedException extends RuntimeException {
    ScenarioRejectedException(String reason) {
        super(reason);
    }
}
