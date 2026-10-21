package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import taan.online.RuntimeRoomTestAccess;
import taan.online.TestFixtures;
import taan.online.match.model.MatchBoxPolicy;
import taan.online.match.service.MatchRegistry;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchLootPolicy;

class ApplicationLootConfigurationTest {
    @Test
    void enablesTheCompleteMinimalCannonProfileInTheActualCompositionRoot() throws Exception {
        try (var application = new Application(ApplicationConfig.fromEnvironment(TestFixtures.environment()))) {
            var matches = RuntimeRoomTestAccess.matches(RuntimeRoomTestAccess.rooms(application));
            var field = MatchRegistry.class.getDeclaredField("settings");
            field.setAccessible(true);
            var settings = (MatchBootstrapSettings) field.get(matches);

            assertEquals(MatchExecutionMode.CLIENT_SCENARIO, settings.executionMode());
            assertEquals(MatchLootPolicy.nativeItems(), settings.lootPolicy());
            assertEquals(MatchBoxPolicy.ENABLED, settings.boxPolicy());
            assertEquals(MatchExecutionMode.PREVIEW, settings.forProfile(false).executionMode());
            assertEquals(MatchBoxPolicy.DISABLED, settings.forProfile(false).boxPolicy());
            assertFalse(settings.forProfile(false).lootPolicy().enabled());
        }
    }
}
