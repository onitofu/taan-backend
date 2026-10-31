package taan.online.match.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.concurrent.CompletableFuture;
import org.slf4j.LoggerFactory;

final class PausingScenarioAckAppender extends AppenderBase<ILoggingEvent> implements AutoCloseable {
    final CompletableFuture<Void> entered = new CompletableFuture<>();
    final CompletableFuture<Void> resume = new CompletableFuture<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(MatchLifecycleCoordinator.class);
    private final Level previous = logger.getLevel();

    PausingScenarioAckAppender() {
        start();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(this);
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getMessage().equals("Scenario acknowledgement accepted")) {
            entered.complete(null);
            resume.join();
        }
    }

    @Override
    public void close() {
        resume.complete(null);
        logger.detachAppender(this);
        logger.setLevel(previous);
        stop();
    }
}
