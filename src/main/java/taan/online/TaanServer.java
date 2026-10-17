package taan.online;

import static taan.online.logging.LogField.setting;

import taan.online.bootstrap.Application;
import taan.online.bootstrap.ApplicationConfig;
import taan.online.bootstrap.InvalidConfigurationException;
import taan.online.bootstrap.logging.ServerLogEvent;
import taan.online.logging.EventLogger;

public class TaanServer {
    private static final EventLogger LOG = EventLogger.forClass(TaanServer.class);

    static void main() {
        try (var application = new Application(ApplicationConfig.fromEnvironment(System.getenv()))) {
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("shutdown").unstarted(application::close));

            application.start();
            application.awaitTermination();
        } catch (InvalidConfigurationException failure) {
            LOG.log(ServerLogEvent.INVALID_CONFIGURATION, setting(failure.setting()));

            System.exit(1);
        } catch (Exception failure) {
            LOG.failure(ServerLogEvent.STARTUP_FAILED, failure);

            System.exit(1);
        }
    }
}
