package taan.online.match.service;

public interface MatchCommandExecutor {
    void execute(Runnable command);

    default int quantum() {
        return 32;
    }
}
