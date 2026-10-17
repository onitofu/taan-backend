package taan.online.bootstrap;

public final class RoomOutputValidator {
    private static final int MAX_INITIAL_ROOT_BYTES = 2458;

    public void validate(ApplicationConfig config) {
        if (config.maxFrameBytes() < MAX_INITIAL_ROOT_BYTES) {
            throw new InvalidConfigurationException(
                "BACKEND_MAX_FRAME_BYTES",
                "Room initialization exceeds frame limit"
            );
        }

        if (config.outputBytes() < MAX_INITIAL_ROOT_BYTES + Integer.BYTES) {
            throw new InvalidConfigurationException(
                "BACKEND_MAX_PENDING_OUTPUT_BYTES",
                "Room initialization exceeds output queue limit"
            );
        }
    }
}
