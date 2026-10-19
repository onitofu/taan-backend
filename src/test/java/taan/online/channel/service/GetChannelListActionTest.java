package taan.online.channel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.SelectionFixtures;
import taan.online.channel.controller.GetChannelListPacketHandler;
import taan.online.channel.model.ChannelDefinition;
import taan.online.channel.model.ChannelDirectory;
import taan.online.channel.model.ChannelGroupDefinition;

class GetChannelListActionTest {
    @Test
    void rejectsUnsupportedKindsIndependentlyOfTheGroupDescriptionType() {
        for (int kind : new int[]{-1, 2, 3, 5, 7}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelDefinition(1, "Moon", true, 1, 109, 100, kind)
            );
        }
    }

    @Test
    void returnsTheConfiguredDirectoryOnlyWhileTheSelectionIsCurrent() {
        for (int allowed : new int[]{0, 1, 2, 3}) {
            var calls = new AtomicInteger();
            SelectedCharacterAccess sessions = id -> calls.incrementAndGet() <= allowed
                ? Optional.of(ChannelTestAccess.participant(id, 1, 1)) : Optional.empty();
            var action = new GetChannelListAction(sessions, SelectionFixtures.registry(sessions));
            var handler = new GetChannelListPacketHandler(action, sessions);
            var result = handler.handle(UUID.randomUUID(), SelectionFixtures.channels());

            assertEquals(allowed == 3, result.isPresent());

            if (result.isPresent()) {
                assertEquals("Solar", result.orElseThrow().groups().getFirst().name());
                assertEquals(
                    List.of("Moon", "Terra"),
                    result.orElseThrow().groups().getFirst().channels().stream()
                        .map(value -> value.name()).toList()
                );
            }
        }

        SelectedCharacterAccess denied = id -> Optional.empty();

        assertTrue(
            new GetChannelListAction(denied, SelectionFixtures.registry(denied)).execute(UUID.randomUUID()).isEmpty()
        );
    }

    @Test
    void rejectsAmbiguousIdentifiersAndUnsupportedNamesTypesAndLimits() {
        for (int id : new int[]{0, 65535}) {
            assertThrows(IllegalArgumentException.class, () -> new ChannelDefinition(id, "Moon", true, 1, 109, 100, 0));
        }

        for (String name : List.of(" ", "x".repeat(65))) {
            assertThrows(IllegalArgumentException.class, () -> new ChannelDefinition(1, name, true, 1, 109, 100, 0));
            assertThrows(IllegalArgumentException.class, () -> new ChannelGroupDefinition(1, name, 0, List.of()));
        }

        for (int[] bounds : new int[][]{{0, 109, 100}, {1, 110, 100}, {2, 1, 100}, {1, 109, 0}}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelDefinition(1, "Moon", true, bounds[0], bounds[1], bounds[2], 0)
            );
        }

        assertThrows(IllegalArgumentException.class, () -> new ChannelGroupDefinition(0, "Solar", 0, List.of()));

        for (int type : new int[]{-1, 7}) {
            assertThrows(IllegalArgumentException.class, () -> new ChannelGroupDefinition(1, "Solar", type, List.of()));
        }

        for (int port : new int[]{0, 65536}) {
            assertThrows(IllegalArgumentException.class, () -> new ChannelDirectory(List.of(), port));
        }

        var group = SelectionFixtures.directory().groups().getFirst();

        assertThrows(IllegalArgumentException.class, () -> new ChannelDirectory(List.of(group, group), 2181));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ChannelDirectory(
                List.of(group, new ChannelGroupDefinition(2, "Other", 1, group.channels())),
                2181
            )
        );
        assertThrows(UnsupportedOperationException.class, () -> SelectionFixtures.directory().groups().clear());
        assertThrows(UnsupportedOperationException.class, () -> group.channels().clear());
        assertEquals(
            0,
            new ChannelDirectory(List.of(new ChannelGroupDefinition(1, "Empty", 6, List.of())), 2181)
                .channels().size()
        );
    }
}
