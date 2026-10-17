package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import taan.online.TestFixtures;

class ApplicationLifecycleTest {
    @Test
    void releasesResourcesWhenClosedBeforeStartupAndAllowsRepeatedCleanup() throws Exception {
        var application = new Application(ApplicationConfig.fromEnvironment(TestFixtures.environment()));
        application.close();

        assertDoesNotThrow(application::close);

        application.awaitTermination();
    }
}
