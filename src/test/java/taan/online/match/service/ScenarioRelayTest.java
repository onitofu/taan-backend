package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.AcceptedScenario;
import taan.online.match.service.model.ScenarioAdmissionOutcome;
import taan.online.match.service.model.ScenarioAdmissionResult;
import taan.online.match.view.ProtocolMatchView;
import taan.online.room.model.RoomId;

class ScenarioRelayTest {
    @Test
    void relaysExactRootOnlyToCurrentObserversAndWaitsForDelivery() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            var result = world.execute(world.claims);
            var observer = world.members.getFirst();
            var wire = new CollectingPacketOutput();
            var shooter = new TestMatchOutput();
            var outsider = new TestMatchOutput();
            var subscriptions = List.of(
                new MatchSubscription(world.sender.sessionId(), shooter),
                new MatchSubscription(observer.sessionId(), new ProtocolMatchView(wire)),
                new MatchSubscription(UUID.randomUUID(), outsider)
            );
            var relay = new ScenarioRelay((context, member) -> true);
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();

            assertEquals(1, wire.batches.size());
            assertEquals(1, wire.batches.getFirst().size());
            assertArrayEquals(world.root, wire.batches.getFirst().getFirst());
            assertTrue(shooter.scenarios.isEmpty());
            assertTrue(outsider.scenarios.isEmpty());
            var pending = new TestMatchOutput();
            pending.delivery = new CompletableFuture<>();
            var delivery = relay.deliver(
                result,
                world.snapshot(),
                List.of(new MatchSubscription(observer.sessionId(), pending))
            ).toCompletableFuture();

            assertTrue(!delivery.isDone());
            pending.delivery.completeExceptionally(new IllegalStateException("transport failed"));
            assertThrows(CompletionException.class, delivery::join);
            world.phase = MatchPhase.COMPLETION;
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            assertEquals(2, wire.batches.size());
        }
    }

    @Test
    void discardsRejectedDuplicateAndIgnoredResults() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            var output = new TestMatchOutput();
            var subscriptions = List.of(new MatchSubscription(world.members.getFirst().sessionId(), output));
            var relay = new ScenarioRelay((context, member) -> true);

            for (var outcome : List.of(
                ScenarioAdmissionOutcome.REJECTED,
                ScenarioAdmissionOutcome.DUPLICATE,
                ScenarioAdmissionOutcome.IGNORED
            )) {
                relay.deliver(
                    new ScenarioAdmissionResult(outcome, "IGNORED", Optional.empty()),
                    world.snapshot(),
                    subscriptions
                ).toCompletableFuture().join();
            }

            assertTrue(output.scenarios.isEmpty());
        }
    }

    @Test
    void revalidatesMatchChannelRoomGenerationAdmissionAndTurnBeforePublishing() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            var result = world.execute(world.claims);
            var output = new TestMatchOutput();
            var subscriptions = List.of(new MatchSubscription(world.members.getFirst().sessionId(), output));
            var relay = new ScenarioRelay((context, member) -> true);
            world.phase = MatchPhase.PREVIEW;
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            world.phase = MatchPhase.ACTION;
            world.id = new MatchId(UUID.randomUUID());
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            world.id = world.original.id();
            world.roomId = new RoomId(2);
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            world.roomId = world.original.roomId();
            world.generation = UUID.randomUUID();
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            world.generation = world.original.roomGeneration();
            world.channel++;
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            world.channel--;
            world.revision++;
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            world.revision--;
            world.members = world.members.subList(0, 1);
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            world.members = world.original.participants();
            new ScenarioRelay((
                context,
                member
            ) -> false).deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            new ScenarioRelay((
                context,
                member
            ) -> context.sessionId().equals(world.sender.sessionId())).deliver(
                result,
                world.snapshot(),
                subscriptions
            ).toCompletableFuture().join();
            var accepted = result.accepted().orElseThrow();
            var staleAdmission = world.context(world.sender.sessionId(), UUID.randomUUID());
            var stale = new AcceptedScenario(staleAdmission, 7, world.root, accepted.journal());
            relay.deliver(
                new ScenarioAdmissionResult(ScenarioAdmissionOutcome.ACCEPTED, "VALIDATED", Optional.of(stale)),
                world.snapshot(),
                subscriptions
            ).toCompletableFuture().join();

            assertTrue(output.scenarios.isEmpty());
        }
    }
}
