package taan.online.channel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.SelectionFixtures;
import taan.online.channel.model.ChannelDefinition;
import taan.online.channel.model.ChannelGroupDefinition;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.channel.service.model.MoveToOutcome;
import taan.online.character.model.CharacterId;
import taan.online.lobby.service.EnterLobbyAction;
import taan.online.lobby.service.LeaveLobbyAction;

class ChannelRegistryTest {
    @Test
    void readsIndependentImmutableLootSettingsForTheRequestedChannel() {
        var registry = new ChannelRegistry(new ChannelTestAccess(), 2181);
        registry.initialize(List.of(new ChannelGroupDefinition(
            1,
            "Solar",
            0,
            List.of(
                new ChannelDefinition(1, "Moon", true, 1, 109, 100, 0, 0),
                new ChannelDefinition(2, "Terra", true, 1, 109, 100, 0, 70)
            )
        )));
        assertEquals(0, registry.lootPickerScalar(1));
        assertEquals(70, registry.lootPickerScalar(2));
        assertThrows(NoSuchElementException.class, () -> registry.lootPickerScalar(3));
        assertThrows(IllegalStateException.class, () -> registry.initialize(List.of()));
        assertEquals(70, registry.lootPickerScalar(2));
    }

    @Test
    void checksTheCurrentSessionProfileAvailabilityLevelAndCapacityBeforeChangingMembership() {
        var access = new ChannelTestAccess();
        var id = access.add(1, 5);
        var registry = registry(access);

        assertEquals(MoveToOutcome.IGNORED, registry.move(UUID.randomUUID(), command(1)).outcome());

        for (var command : List.of(
            new MoveToCommand(1, 1, 0, 929),
            new MoveToCommand(0, 1, 1, 929),
            new MoveToCommand(0, 1, 0, 928)
        )) {
            assertEquals(MoveToOutcome.UNSUPPORTED, registry.move(id, command).outcome());
        }

        assertEquals(MoveToOutcome.UNKNOWN, registry.move(id, command(99)).outcome());
        assertEquals(MoveToOutcome.DISABLED, registry.move(id, command(3)).outcome());
        assertEquals(MoveToOutcome.LEVEL_TOO_LOW, registry.move(id, command(2)).outcome());
        var high = access.add(2, 20);

        assertEquals(MoveToOutcome.LEVEL_TOO_HIGH, registry.move(high, command(1)).outcome());
        assertEquals(MoveToOutcome.MOVED, registry.move(id, command(1)).outcome());
        assertEquals(MoveToOutcome.MOVED, registry.move(id, command(1)).outcome());
        assertEquals(100, registry.catalog().fills().get(1));
        assertEquals(MoveToOutcome.FULL, registry.move(access.add(3, 5), command(1)).outcome());
        assertEquals(MoveToOutcome.MOVED, registry.move(high, command(2)).outcome());
        assertEquals(MoveToOutcome.FULL, registry.move(access.add(4, 20), command(2)).outcome());
        registry.release(id, "DISCONNECT");

        assertEquals(0, registry.catalog().fills().get(1));
        assertEquals(MoveToOutcome.MOVED, registry.move(access.add(5, 5), command(1)).outcome());
    }

    @Test
    void separatesChannelSeatsFromLobbyRowsAndKeepsSnapshotsAndDeltasIdempotent() {
        var access = new ChannelTestAccess();
        var first = access.add(1, 20);
        var second = access.add(2, 20);
        var third = access.add(3, 5);
        var registry = registry(access);
        var a = new RecordingChannelOutput();
        var b = new RecordingChannelOutput();
        var c = new RecordingChannelOutput();
        var enter = new EnterLobbyAction(registry);
        var leave = new LeaveLobbyAction(registry);

        assertEquals(ChannelPresencePhase.NONE, enter.execute(first, a).phase());
        assertEquals(1, a.unavailable);
        assertEquals(ChannelPresencePhase.NONE, leave.execute(first, a).phase());
        assertEquals(1, a.left);

        assertEquals(MoveToOutcome.MOVED, registry.move(first, command(2)).outcome());
        enter.execute(first, a);
        enter.execute(first, a);

        assertEquals(2, a.snapshots.size());
        assertEquals(1, a.population);
        assertEquals(List.of(access.players.get(first)), a.snapshots.getFirst());
        assertTrue(a.added.isEmpty());

        assertEquals(MoveToOutcome.MOVED, registry.move(third, command(1)).outcome());
        enter.execute(third, c);
        registry.release(third, "DISCONNECT");

        assertTrue(a.added.isEmpty());
        assertTrue(a.removed.isEmpty());
        assertEquals(MoveToOutcome.FULL, registry.move(second, command(2)).outcome());
        assertEquals(MoveToOutcome.LEVEL_TOO_HIGH, registry.move(first, command(1)).outcome());
        assertEquals(ChannelPresencePhase.MEMBER, leave.execute(first, a).phase());
        assertEquals(100, registry.catalog().fills().get(2));
        assertEquals(ChannelPresencePhase.MEMBER, leave.execute(first, a).phase());
        assertEquals(MoveToOutcome.FULL, registry.move(second, command(2)).outcome());

        enter.execute(first, a);
        registry.release(first, "DISCONNECT");
        registry.release(first, "DISCONNECT");
        registry.release(null, "DISCONNECT");

        assertEquals(0, registry.catalog().fills().get(2));
        assertEquals(MoveToOutcome.MOVED, registry.move(second, command(2)).outcome());
        enter.execute(second, b);

        assertEquals(List.of(access.players.get(second)), b.snapshots.getFirst());
        access.players.remove(second);

        assertEquals(ChannelPresencePhase.CLOSED, registry.enter(second, b).phase());
        assertEquals(ChannelPresencePhase.CLOSED, registry.leave(second, b).phase());
    }

    @Test
    void isolatesSnapshotsDeltasPopulationAndCleanupByServerAssignedChannel() {
        var access = new ChannelTestAccess();
        var registry = SelectionFixtures.registry(access);
        var first = access.add(1, 1);
        var second = access.add(2, 1);
        var third = access.add(3, 1);
        var fourth = access.add(4, 1);
        var a = new RecordingChannelOutput();
        var b = new RecordingChannelOutput();
        var c = new RecordingChannelOutput();
        var d = new RecordingChannelOutput();

        registry.move(first, command(1));
        registry.enter(first, a);
        registry.move(second, command(1));
        registry.enter(second, b);
        registry.move(third, command(2));
        registry.enter(third, c);
        registry.move(fourth, command(2));
        registry.enter(fourth, d);

        assertEquals(List.of(access.players.get(first), access.players.get(second)), b.snapshots.getFirst());
        assertEquals(List.of(access.players.get(third)), c.snapshots.getFirst());
        assertEquals(List.of(access.players.get(third), access.players.get(fourth)), d.snapshots.getFirst());
        assertEquals(List.of(access.players.get(second)), a.added);
        assertTrue(b.added.isEmpty());
        assertEquals(List.of(access.players.get(fourth)), c.added);
        assertTrue(d.added.isEmpty());
        assertEquals(2, b.population);
        assertEquals(2, d.population);
        assertEquals(2, registry.catalog().fills().get(1));
        assertEquals(2, registry.catalog().fills().get(2));

        registry.move(second, command(2));

        assertEquals(List.of(new CharacterId(2)), a.removed);
        assertEquals(List.of(access.players.get(fourth)), c.added);
        assertTrue(d.added.isEmpty());
        assertEquals(1, registry.catalog().fills().get(1));
        assertEquals(3, registry.catalog().fills().get(2));

        registry.enter(second, b);

        assertEquals(
            List.of(access.players.get(second), access.players.get(third), access.players.get(fourth)),
            b.snapshots.getLast()
        );
        assertEquals(List.of(access.players.get(fourth), access.players.get(second)), c.added);
        assertEquals(List.of(access.players.get(second)), d.added);
        assertEquals(List.of(access.players.get(second)), a.added);

        assertEquals(MoveToOutcome.UNKNOWN, registry.move(second, command(99)).outcome());
        registry.leave(second, b);
        registry.release(fourth, "DISCONNECT");

        assertEquals(List.of(new CharacterId(2)), a.removed);
        assertEquals(List.of(new CharacterId(2), new CharacterId(4)), c.removed);
        assertEquals(List.of(new CharacterId(2)), d.removed);
        assertTrue(b.removed.isEmpty());
        assertEquals(1, registry.catalog().fills().get(1));
        assertEquals(2, registry.catalog().fills().get(2));
    }

    @Test
    void notifiesOnlySubscribersAndRemovesTheOldSessionBeforeReconnectingTheSameCharacter() {
        var access = new ChannelTestAccess();
        var registry = new ChannelRegistry(access, 2181);
        registry.initialize(
            List.of(
                new ChannelGroupDefinition(
                    1,
                    "Solar",
                    0,
                    List.of(
                        new ChannelDefinition(1, "Moon", true, 1, 109, 100, 0, 0),
                        new ChannelDefinition(2, "Terra", true, 1, 109, 100, 0, 0)
                    )
                )
            )
        );
        var first = access.add(1, 1);
        var second = access.add(2, 1);
        var a = new RecordingChannelOutput();
        var b = new RecordingChannelOutput();
        registry.move(first, command(1));
        registry.enter(first, a);
        registry.move(second, command(1));
        registry.enter(second, b);
        registry.enter(second, b);

        assertEquals(List.of(access.players.get(second)), a.added);
        assertEquals(2, b.population);
        assertEquals(2, b.snapshots.getFirst().size());

        registry.leave(second, b);
        registry.leave(second, b);

        assertEquals(List.of(new CharacterId(2)), a.removed);

        registry.enter(second, b);
        registry.move(second, command(2));

        assertEquals(List.of(new CharacterId(2), new CharacterId(2)), a.removed);
        assertEquals(1, registry.catalog().fills().get(1));
        assertEquals(1, registry.catalog().fills().get(2));

        registry.move(second, command(1));
        registry.enter(second, b);
        access.players.remove(second);
        var replacement = access.add(2, 1);
        registry.move(replacement, command(1));
        registry.enter(replacement, new RecordingChannelOutput());
        registry.release(second, "LATE_DISCONNECT");

        assertEquals(2, registry.catalog().fills().get(1));
        assertEquals(3, a.removed.size());
        assertEquals(4, a.added.size());
    }

    @Test
    void admitsOnlyOneConcurrentRequestForTheLastSeat() throws Exception {
        var access = new ChannelTestAccess();
        var first = access.add(1, 1);
        var second = access.add(2, 1);
        var registry = registry(access);
        var barrier = new CyclicBarrier(2);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return registry.move(first, command(1)).outcome();
                }
            );
            var b = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return registry.move(second, command(1)).outcome();
                }
            );
            var outcomes = List.of(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));

            assertTrue(outcomes.contains(MoveToOutcome.MOVED));
            assertTrue(outcomes.contains(MoveToOutcome.FULL));
            assertEquals(100, registry.catalog().fills().get(1));
        }
    }

    @Test
    void freezesConfigurationAndBoundsParticipantLevels() {
        var access = new ChannelTestAccess();
        var registry = new ChannelRegistry(access, 2181);

        assertThrows(NullPointerException.class, registry::catalog);
        registry.initialize(List.of());

        assertThrows(IllegalStateException.class, () -> registry.initialize(List.of()));
        assertTrue(registry.catalog().fills().isEmpty());

        for (int level : new int[]{0, 65536}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> ChannelTestAccess.participant(
                    UUID.randomUUID(),
                    1,
                    level
                )
            );
        }
    }

    @Test
    void rejectsTwoLiveSessionsForOneCharacterAndDropsEntryIfItsSessionClosesDuringBootstrap() {
        var access = new ChannelTestAccess();
        var first = access.add(1, 1);
        var duplicate = access.add(1, 1);
        var registry = registry(access);
        registry.move(first, command(1));

        assertThrows(IllegalStateException.class, () -> registry.move(duplicate, command(1)));

        var view = new RecordingChannelOutput();
        view.onInitialize = () -> access.players.remove(first);

        assertEquals(ChannelPresencePhase.CLOSED, registry.enter(first, view).phase());
        assertEquals(0, registry.catalog().fills().get(1));

        registry.move(duplicate, command(1));
        view.onInitialize = () -> registry.release(duplicate, "TRANSPORT_CLOSED");

        assertEquals(ChannelPresencePhase.CLOSED, registry.enter(duplicate, view).phase());
        assertEquals(0, registry.catalog().fills().get(1));
    }

    @Test
    void keepsAConcurrentSnapshotAndSubscriptionConsistentWithoutDuplicateAdds() throws Exception {
        var access = new ChannelTestAccess();
        var first = access.add(1, 1);
        var second = access.add(2, 1);
        var registry = SelectionFixtures.registry(access);
        var a = new RecordingChannelOutput();
        var b = new RecordingChannelOutput();
        registry.move(first, command(1));
        registry.move(second, command(1));
        var barrier = new CyclicBarrier(2);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var one = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return registry.enter(first, a);
                }
            );
            var two = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return registry.enter(second, b);
                }
            );
            one.get(5, TimeUnit.SECONDS);
            two.get(5, TimeUnit.SECONDS);
        }

        for (var output : List.of(a, b)) {
            var visible = new HashSet<CharacterId>();
            output.snapshots.getFirst().forEach(player -> assertTrue(visible.add(player.characterId())));
            output.added.forEach(player -> assertTrue(visible.add(player.characterId())));

            assertEquals(Set.of(new CharacterId(1), new CharacterId(2)), visible);
        }
    }

    private ChannelRegistry registry(ChannelTestAccess access) {
        var registry = new ChannelRegistry(access, 2181);
        registry.initialize(
            List.of(
                new ChannelGroupDefinition(
                    1,
                    "Solar",
                    0,
                    List.of(
                        new ChannelDefinition(1, "Moon", true, 1, 10, 1, 0, 0),
                        new ChannelDefinition(2, "Terra", true, 20, 30, 1, 0, 0),
                        new ChannelDefinition(3, "Closed", false, 1, 109, 1, 0, 0)
                    )
                )
            )
        );
        return registry;
    }

    private MoveToCommand command(int id) {
        return new MoveToCommand(0, id, 0, 929);
    }
}
