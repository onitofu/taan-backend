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
        assertEquals(2, config.matchWorkerThreads());
        assertEquals(64, config.matchQueueCapacity());
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
    void requiresPositiveMatchExecutionLimits() {
        for (String setting : new String[]{"BACKEND_MATCH_WORKER_THREADS", "BACKEND_MATCH_QUEUE_CAPACITY"}) {
            var environment = TestFixtures.environment();
            environment.remove(setting);

            assertThrows(InvalidConfigurationException.class, () -> ApplicationConfig.fromEnvironment(environment));

            for (String value : new String[]{"0", "-1", "invalid"}) {
                environment.put(setting, value);
                var failure = assertThrows(
                    InvalidConfigurationException.class,
                    () -> ApplicationConfig.fromEnvironment(environment)
                );

                assertEquals(setting, failure.setting());
            }
        }
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
    void acceptsConfiguredActionAndCompletionTimeoutsAndDefaultsForExistingEnvironments() {
        var environment = TestFixtures.environment();
        environment.remove("BACKEND_MATCH_ACTION_TIMEOUT_SECONDS");
        environment.remove("BACKEND_MATCH_COMPLETION_GRACE_SECONDS");
        var defaults = ApplicationConfig.fromEnvironment(environment);
        assertEquals(20, defaults.matchActionTimeoutSeconds());
        assertEquals(10, defaults.matchCompletionGraceSeconds());
        environment.put("BACKEND_MATCH_ACTION_TIMEOUT_SECONDS", "7");
        environment.put("BACKEND_MATCH_COMPLETION_GRACE_SECONDS", "3");
        var configured = ApplicationConfig.fromEnvironment(environment);
        assertEquals(7, configured.matchActionTimeoutSeconds());
        assertEquals(3, configured.matchCompletionGraceSeconds());
        environment.put("BACKEND_MATCH_COMPLETION_GRACE_SECONDS", "0");
        assertThrows(InvalidConfigurationException.class, () -> ApplicationConfig.fromEnvironment(environment));
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

    @Test
    void boundsIndependentResourcesAndRequiresEnoughConnectionsAndAShorterAcquireTimeout() {
        var environment = TestFixtures.environment();
        environment.put("DB_POOL_SIZE", "4");
        assertEquals(
            "DB_POOL_SIZE",
            assertThrows(
                InvalidConfigurationException.class,
                () -> ApplicationConfig.fromEnvironment(environment)
            ).setting()
        );

        environment.put("DB_POOL_SIZE", "6");
        environment.put("DB_ACQUIRE_TIMEOUT_MILLIS", "10000");
        assertEquals(
            "DB_ACQUIRE_TIMEOUT_MILLIS",
            assertThrows(
                InvalidConfigurationException.class,
                () -> ApplicationConfig.fromEnvironment(environment)
            ).setting()
        );

        for (String key : new String[]{
            "BACKEND_AUTH_WORKER_THREADS",
            "BACKEND_AUTH_QUEUE_CAPACITY",
            "BACKEND_DATABASE_WORKER_THREADS",
            "BACKEND_DATABASE_QUEUE_CAPACITY",
            "BACKEND_REQUEST_WORKER_THREADS",
            "BACKEND_REQUEST_QUEUE_CAPACITY",
            "BACKEND_SETTLEMENT_WORKER_THREADS",
            "BACKEND_SETTLEMENT_QUEUE_CAPACITY",
            "DB_POOL_SIZE",
            "DB_ACQUIRE_TIMEOUT_MILLIS",
            "BACKEND_MATCH_COMMAND_QUANTUM"
        }) {
            var invalid = TestFixtures.environment();
            invalid.remove(key);
            assertEquals(
                key,
                assertThrows(
                    InvalidConfigurationException.class,
                    () -> ApplicationConfig.fromEnvironment(invalid)
                ).setting()
            );

            for (String value : new String[]{"0", "2147483647"}) {
                invalid.put(key, value);
                assertThrows(InvalidConfigurationException.class, () -> ApplicationConfig.fromEnvironment(invalid));
            }
        }
    }
}
