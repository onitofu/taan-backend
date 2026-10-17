package taan.online.bootstrap;

import java.util.Map;
import java.util.Set;

public record ApplicationConfig(
    String bindAddress,
    int port,
    String databaseHost,
    int databasePort,
    String databaseName,
    String databaseUser,
    String databasePassword,
    int maxConnections,
    int maxFrameBytes,
    int maxPendingRequests,
    int workerThreads,
    int workerQueueCapacity,
    int requestTimeoutSeconds,
    int frameAssemblyTimeoutSeconds,
    int idleTimeoutSeconds,
    int shutdownTimeoutSeconds,
    int connectTimeoutSeconds,
    int socketTimeoutSeconds,
    int argonMemoryKiB,
    int argonIterations,
    int argonParallelism,
    String logLevel,
    int logWarnBurst,
    int logWarnIntervalSeconds,
    int outputBatches,
    int outputBytes,
    int playableTimeSeconds
) {
    public static ApplicationConfig fromEnvironment(Map<String, String> environment) {
        String level = required(environment, "LOG_LEVEL");

        if (!Set.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE").contains(level)) {
            throw new InvalidConfigurationException("LOG_LEVEL", "Unsupported log level");
        }

        var config = new ApplicationConfig(
            required(environment, "BACKEND_BIND_ADDRESS"),
            number(environment, "BACKEND_PORT", 1, 65535),
            required(environment, "DB_HOSTNAME"),
            number(environment, "DB_PORT", 1, 65535),
            required(environment, "POSTGRES_DB"),
            required(environment, "POSTGRES_USER"),
            required(environment, "POSTGRES_PASSWORD"),
            positive(environment, "BACKEND_MAX_CONNECTIONS"),
            number(environment, "BACKEND_MAX_FRAME_BYTES", 4, Integer.MAX_VALUE - 4),
            positive(environment, "BACKEND_MAX_PENDING_REQUESTS"),
            positive(environment, "BACKEND_WORKER_THREADS"),
            positive(environment, "BACKEND_WORK_QUEUE_CAPACITY"),
            positive(environment, "BACKEND_REQUEST_TIMEOUT_SECONDS"),
            positive(environment, "BACKEND_FRAME_ASSEMBLY_TIMEOUT_SECONDS"),
            positive(environment, "BACKEND_IDLE_TIMEOUT_SECONDS"),
            number(environment, "BACKEND_SHUTDOWN_TIMEOUT_SECONDS", 4, Integer.MAX_VALUE),
            positive(environment, "DB_CONNECT_TIMEOUT_SECONDS"),
            positive(environment, "DB_SOCKET_TIMEOUT_SECONDS"),
            number(environment, "ARGON2_MEMORY_KIB", 19456, Integer.MAX_VALUE),
            number(environment, "ARGON2_ITERATIONS", 2, Integer.MAX_VALUE),
            positive(environment, "ARGON2_PARALLELISM"),
            level,
            positive(environment, "LOG_WARN_BURST"),
            positive(environment, "LOG_WARN_INTERVAL_SECONDS"),
            positive(environment, "BACKEND_MAX_PENDING_OUTPUT_BATCHES"),
            positive(environment, "BACKEND_MAX_PENDING_OUTPUT_BYTES"),
            positive(environment, "BACKEND_PLAYABLE_TIME_BOOTSTRAP_SECONDS")
        );

        if (config.argonMemoryKiB() < 8L * config.argonParallelism()) {
            throw new InvalidConfigurationException("ARGON2_PARALLELISM", "Insufficient memory per lane");
        }

        return config;
    }

    private static int positive(Map<String, String> environment, String name) {
        return number(environment, name, 1, Integer.MAX_VALUE);
    }

    private static int number(Map<String, String> environment, String name, int minimum, int maximum) {
        int value;

        try {
            value = Integer.parseInt(required(environment, name));
        } catch (NumberFormatException failure) {
            throw new InvalidConfigurationException(name, "Invalid numeric setting");
        }

        if (value < minimum || value > maximum) {
            throw new InvalidConfigurationException(name, "Setting outside supported range");
        }

        return value;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);

        if (value == null || value.isBlank()) {
            throw new InvalidConfigurationException(name, "Missing setting");
        }

        return value;
    }

    @Override
    public String toString() {
        return "ApplicationConfig[REDACTED]";
    }
}
