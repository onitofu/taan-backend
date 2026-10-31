package taan.online;

import static taan.online.logging.LogField.setting;

import java.io.IOException;
import java.util.Properties;
import taan.online.bootstrap.Application;
import taan.online.bootstrap.ApplicationConfig;
import taan.online.bootstrap.InvalidConfigurationException;
import taan.online.bootstrap.logging.ServerLogEvent;
import taan.online.logging.EventLogger;

public class TaanServer {
    private static final EventLogger LOG = EventLogger.forClass(TaanServer.class);

    static void main() {
        try {
            printBanner();

            try (var application = new Application(ApplicationConfig.fromEnvironment(System.getenv()))) {
                Runtime.getRuntime().addShutdownHook(
                    Thread.ofPlatform().name("shutdown").unstarted(application::close)
                );

                application.start();
                application.awaitTermination();
            }
        } catch (InvalidConfigurationException failure) {
            LOG.log(ServerLogEvent.INVALID_CONFIGURATION, setting(failure.setting()));

            System.exit(1);
        } catch (Exception failure) {
            LOG.failure(ServerLogEvent.STARTUP_FAILED, failure);

            System.exit(1);
        }
    }

    private static void printBanner() throws IOException {
        var build = new Properties();

        try (var input = TaanServer.class.getResourceAsStream("/build-info.properties")) {
            if (input != null) {
                build.load(input);
            }
        }

        System.out.println(
            """
              _________    ___    _   __
             /_  __/   |  /   |  / | / /
              / / / /| | / /| | /  |/ /
             / / / ___ |/ ___ |/ /|  /
            /_/ /_/  |_/_/  |_/_/ |_/

            [Taan Online] ◀▶ (v%s)
            """.formatted(build.getProperty("application.version", "development"))
        );
    }
}
