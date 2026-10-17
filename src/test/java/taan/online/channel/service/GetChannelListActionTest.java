package taan.online.channel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.SelectionFixtures;
import taan.online.channel.controller.GetChannelListPacketHandler;
import taan.online.channel.model.ChannelDefinition;
import taan.online.channel.model.ChannelDirectory;

class GetChannelListActionTest {
    @Test
    void returnsTheConfiguredDirectoryOnlyWhileTheSelectionIsCurrent() {
        for (int allowed : new int[]{0, 1, 2, 3}) {
            var calls = new AtomicInteger();
            SelectedCharacterAccess sessions = ignored -> calls.incrementAndGet() <= allowed;
            var action = new GetChannelListAction(sessions, SelectionFixtures.directory());
            var handler = new GetChannelListPacketHandler(action, sessions);
            var result = handler.handle(UUID.randomUUID(), SelectionFixtures.channels());

            assertEquals(allowed == 3, result.isPresent());

            if (result.isPresent()) {
                assertEquals("Solar", result.orElseThrow().groupName());
                assertEquals(
                    List.of("Moon", "Terra"),
                    result.orElseThrow().channels().stream().map(
                        value -> value.name()
                    ).toList()
                );
            }
        }

        var denied = new GetChannelListAction(ignored -> false, SelectionFixtures.directory());

        assertTrue(denied.execute(UUID.randomUUID()).isEmpty());
    }

    @Test
    void rejectsInvalidDefinitionsAndKeepsReferenceConfigurationImmutable() {
        for (int id : new int[]{0, 65535}) {
            assertThrows(IllegalArgumentException.class, () -> new ChannelDefinition(id, "Moon"));
        }

        assertThrows(IllegalArgumentException.class, () -> new ChannelDefinition(1, " "));
        assertThrows(IllegalArgumentException.class, () -> new ChannelDirectory(" ", 1, List.of()));

        for (int port : new int[]{0, 65536}) {
            assertThrows(IllegalArgumentException.class, () -> new ChannelDirectory("Solar", port, List.of()));
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> new ChannelDirectory(
                "Solar",
                1,
                List.of(
                    new ChannelDefinition(1, "Moon"),
                    new ChannelDefinition(1, "Terra")
                )
            )
        );
        assertThrows(UnsupportedOperationException.class, () -> SelectionFixtures.directory().channels().clear());
    }
}
