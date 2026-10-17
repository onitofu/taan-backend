package taan.online.bootstrap;

import taan.online.channel.model.ChannelCatalogSnapshot;
import taan.online.lobby.protocol.codec.LobbyRoomListBatchCodec;

public final class LobbyOutputValidator {
    public void validate(ApplicationConfig config, ChannelCatalogSnapshot catalog, int catalogBytes) {
        long largestRoster = catalog.directory().channels().stream()
            .mapToLong(channel -> 12L + 51L * Math.min(channel.capacity(), config.maxConnections())).max().orElse(12);
        int entryBytes = LobbyRoomListBatchCodec.MAX_PUBLIC_ENTRY_BYTES;
        int headerBytes = LobbyRoomListBatchCodec.HEADER_BYTES;

        if (catalogBytes > config.maxFrameBytes() || largestRoster > config.maxFrameBytes()
            || config.maxFrameBytes() < entryBytes + headerBytes) {
            throw new InvalidConfigurationException("BACKEND_MAX_FRAME_BYTES", "Lobby snapshots exceed frame limit");
        }

        long roomsPerFrame = (config.maxFrameBytes() - headerBytes) / entryBytes;
        long largestBatch = catalog.directory().channels().stream().mapToLong(
            channel -> {
                long players = Math.min(channel.capacity(), config.maxConnections());
                long rooms = Math.min(players, 65535);
                long chunks = Math.max(1, (rooms + roomsPerFrame - 1) / roomsPerFrame);
                return 29L + 51L * players + entryBytes * rooms + 12L * chunks;
            }
        ).max().orElse(41);

        if (largestBatch > config.outputBytes()) {
            throw new InvalidConfigurationException(
                "BACKEND_MAX_PENDING_OUTPUT_BYTES",
                "Lobby bootstrap exceeds output queue limit"
            );
        }
    }
}
