package taan.online.logging;

final class CountedLogException extends RuntimeException {
    private int stackTraceReads;

    CountedLogException() {
        super("secret-message");
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        stackTraceReads++;
        return super.getStackTrace();
    }

    int stackTraceReads() {
        return stackTraceReads;
    }
}
