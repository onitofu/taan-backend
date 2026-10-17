package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import taan.online.TestFixtures;

class ApplicationConfigTest {
    @Test
    void loadsExampleConfigurationWithoutRevealingSecrets() {
        var config = ApplicationConfig.fromEnvironment(TestFixtures.environment());

        assertEquals(2181, config.port());
        assertEquals(5432, config.databasePort());
        assertEquals(16384, config.maxFrameBytes());
        assertEquals(8, config.maxPendingRequests());
        assertEquals(10, config.frameAssemblyTimeoutSeconds());
        assertEquals("postgres", config.databaseHost());
        assertFalse(config.toString().contains(config.databasePassword()));
    }

    @Test
    void rejectsMissingBlankInvalidAndOutOfRangeSettingsWithoutEchoingValues() {
        var environment = TestFixtures.environment();
        environment.remove("POSTGRES_PASSWORD");

        assertThrows(IllegalArgumentException.class, () -> ApplicationConfig.fromEnvironment(environment));

        environment.put("POSTGRES_PASSWORD", " ");

        assertThrows(IllegalArgumentException.class, () -> ApplicationConfig.fromEnvironment(environment));

        environment.put("POSTGRES_PASSWORD", "secret");
        environment.put("BACKEND_PORT", "secret-port");
        var failure = assertThrows(
            IllegalArgumentException.class,
            () -> ApplicationConfig.fromEnvironment(environment)
        );

        assertFalse(failure.getMessage().contains("secret-port"));
        assertEquals("BACKEND_PORT", ((InvalidConfigurationException) failure).setting());

        environment.put("BACKEND_PORT", "0");

        assertThrows(IllegalArgumentException.class, () -> ApplicationConfig.fromEnvironment(environment));

        environment.put("BACKEND_PORT", "65536");

        assertThrows(IllegalArgumentException.class, () -> ApplicationConfig.fromEnvironment(environment));

        environment.put("BACKEND_PORT", "2181");
        environment.put("LOG_LEVEL", "invalid");

        assertThrows(IllegalArgumentException.class, () -> ApplicationConfig.fromEnvironment(environment));
    }

    @Test
    void requiresPositiveFrameAssemblyTimeout() {
        var environment = TestFixtures.environment();
        environment.remove("BACKEND_FRAME_ASSEMBLY_TIMEOUT_SECONDS");

        assertThrows(InvalidConfigurationException.class, () -> ApplicationConfig.fromEnvironment(environment));

        for (String value : new String[]{"0", "-1", "bad"}) {
            environment.put("BACKEND_FRAME_ASSEMBLY_TIMEOUT_SECONDS", value);
            var failure = assertThrows(
                InvalidConfigurationException.class,
                () -> ApplicationConfig.fromEnvironment(environment)
            );

            assertEquals("BACKEND_FRAME_ASSEMBLY_TIMEOUT_SECONDS", failure.setting());
        }
    }

    @Test
    void rejectsInsufficientArgonMemoryPerLane() {
        var environment = TestFixtures.environment();
        environment.put("ARGON2_PARALLELISM", "2500");
        var failure = assertThrows(
            InvalidConfigurationException.class,
            () -> ApplicationConfig.fromEnvironment(environment)
        );

        assertEquals("ARGON2_PARALLELISM", failure.setting());
    }
}
