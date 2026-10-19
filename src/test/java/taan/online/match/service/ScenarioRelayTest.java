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
import taan.online.match.model.MatchWorldState;
import taan.online.match.model.ScenarioCommitReceipt;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.AcceptedScenario;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.CommittedScenario;
import taan.online.match.service.model.ScenarioApplicationResult;
import taan.online.match.view.ProtocolMatchView;
import taan.online.room.model.RoomId;

class ScenarioRelayTest {
    @Test
    void relaysExactRootOnlyToCurrentObserversAndWaitsForDelivery() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            var result = world.appliedResult();
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
    void refusesMissingOrWrongCommitReceiptsAndStopsPublishingAfterTheOwnerCloses() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            var result = world.appliedResult();
            var output = new TestMatchOutput();
            var subscriptions = List.of(new MatchSubscription(world.members.getFirst().sessionId(), output));
            var relay = new ScenarioRelay((context, member) -> true);
            var applied = world.applied;
            world.applied = new MatchWorldState(
                applied.revision() + 1,
                applied.actors(),
                applied.terrain(),
                applied.falls(),
                applied.processedDeaths(),
                applied.receipt()
            );
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            world.applied = new MatchWorldState(
                applied.revision(),
                applied.actors(),
                applied.terrain(),
                applied.falls(),
                applied.processedDeaths(),
                Optional.empty()
            );
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            var receipt = applied.receipt().orElseThrow();
            world.applied = new MatchWorldState(
                applied.revision(),
                applied.actors(),
                applied.terrain(),
                applied.falls(),
                applied.processedDeaths(),
                Optional.of(new ScenarioCommitReceipt(
                    "wrong",
                    receipt.sessionId(),
                    receipt.admissionId(),
                    7,
                    applied.revision()
                ))
            );
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            world.applied = applied;
            new ScenarioRelay((context, member) -> true, () -> false).deliver(
                result,
                world.snapshot(),
                subscriptions
            ).toCompletableFuture().join();
            assertTrue(output.scenarios.isEmpty());
        }
    }

    @Test
    void discardsRejectedDuplicateAndIgnoredResults() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            var output = new TestMatchOutput();
            var subscriptions = List.of(new MatchSubscription(world.members.getFirst().sessionId(), output));
            var relay = new ScenarioRelay((context, member) -> true);

            for (var outcome : List.of(
                ScenarioApplicationOutcome.REJECTED,
                ScenarioApplicationOutcome.DUPLICATE,
                ScenarioApplicationOutcome.IGNORED
            )) {
                relay.deliver(
                    new ScenarioApplicationResult(outcome, "IGNORED", Optional.empty()),
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
            var result = world.appliedResult();
            var output = new TestMatchOutput();
            var subscriptions = List.of(new MatchSubscription(world.members.getFirst().sessionId(), output));
            var relay = new ScenarioRelay((context, member) -> true);
            world.phase = MatchPhase.PREVIEW;
            relay.deliver(result, world.snapshot(), subscriptions).toCompletableFuture().join();
            world.phase = MatchPhase.COMPLETION;
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
            var accepted = result.committed().orElseThrow().scenario();
            var staleAdmission = world.context(world.sender.sessionId(), UUID.randomUUID());
            var stale = new AcceptedScenario(staleAdmission, 7, world.root, accepted.journal());
            relay.deliver(
                new ScenarioApplicationResult(
                    ScenarioApplicationOutcome.APPLIED,
                    "VALIDATED",
                    Optional.of(new CommittedScenario(stale, world.snapshot(), 0, 0))
                ),
                world.snapshot(),
                subscriptions
            ).toCompletableFuture().join();

            assertTrue(output.scenarios.isEmpty());
        }
    }
}
