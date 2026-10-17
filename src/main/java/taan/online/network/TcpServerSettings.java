package taan.online.network;

public record TcpServerSettings(
    String bindAddress,
    int port,
    int maxConnections,
    int maxFrameBytes,
    int idleTimeoutSeconds,
    int writeTimeoutSeconds,
    int frameAssemblyTimeoutSeconds
) {
}
