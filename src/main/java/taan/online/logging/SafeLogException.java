package taan.online.logging;

public final class SafeLogException extends RuntimeException {
    public SafeLogException(Throwable failure) {
        this(failure, 0);
    }

    private SafeLogException(Throwable failure, int depth) {
        super(failure instanceof SafeLogException ? failure.getMessage() : failure.getClass().getName());
        setStackTrace(failure.getStackTrace());

        if (depth < 4 && failure.getCause() != null) {
            initCause(new SafeLogException(failure.getCause(), depth + 1));
        }
    }
}
