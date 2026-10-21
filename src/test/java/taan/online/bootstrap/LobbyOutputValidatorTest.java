package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import taan.online.SelectionFixtures;
import taan.online.TestFixtures;
import taan.online.channel.model.ChannelCatalogSnapshot;
import taan.online.channel.model.ChannelDefinition;
import taan.online.channel.model.ChannelDirectory;
import taan.online.channel.model.ChannelGroupDefinition;

class LobbyOutputValidatorTest {
    @Test
    void acceptsDefaultBudgetsAndRejectsOversizedCatalogRosterAndSingleRoomEntries() {
        var config = ApplicationConfig.fromEnvironment(TestFixtures.environment());
        var validator = new LobbyOutputValidator();
        var empty = new ChannelCatalogSnapshot(new ChannelDirectory(List.of(), 2181), Map.of());

        validator.validate(config, empty, 0);
        validator.validate(config, new ChannelCatalogSnapshot(SelectionFixtures.directory(), Map.of()), 0);
        assertThrows(
            InvalidConfigurationException.class,
            () -> validator.validate(config, empty, config.maxFrameBytes() + 1)
        );
        var huge = catalog(Integer.MAX_VALUE);
        var env = TestFixtures.environment();
        env.put("BACKEND_MAX_CONNECTIONS", "100000");
        var manyConnections = ApplicationConfig.fromEnvironment(env);

        assertThrows(InvalidConfigurationException.class, () -> validator.validate(manyConnections, huge, 0));
        env.put("BACKEND_MAX_FRAME_BYTES", "542");
        var smallFrame = ApplicationConfig.fromEnvironment(env);

        assertThrows(InvalidConfigurationException.class, () -> validator.validate(smallFrame, empty, 0));
    }

    @Test
    void includesChunkHeadersAndFramePrefixesInTheBootstrapBudget() {
        var env = TestFixtures.environment();
        env.put("BACKEND_MAX_FRAME_BYTES", "543");
        env.put("BACKEND_MAX_CONNECTIONS", "1");
        env.put("BACKEND_MAX_PENDING_OUTPUT_BYTES", "627");
        var validator = new LobbyOutputValidator();
        var one = catalog(1);

        validator.validate(ApplicationConfig.fromEnvironment(env), one, 0);
        env.put("BACKEND_MAX_PENDING_OUTPUT_BYTES", "626");

        assertThrows(
            InvalidConfigurationException.class,
            () -> validator.validate(ApplicationConfig.fromEnvironment(env), one, 0)
        );
    }

    private ChannelCatalogSnapshot catalog(int capacity) {
        var channel = new ChannelDefinition(1, "Channel", true, 1, 109, capacity, 0, 0);
        return new ChannelCatalogSnapshot(
            new ChannelDirectory(List.of(new ChannelGroupDefinition(1, "Solar", 0, List.of(channel))), 2181),
            Map.of()
        );
    }
}
