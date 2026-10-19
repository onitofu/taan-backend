package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.TestMatchOutput;
import taan.online.match.view.ProtocolMatchView;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomStartOutcome;

class MatchMapBootstrapTest {
    @Test
    void loadsEveryOrdinaryMapWithTwoAndEightPlayersWithoutReissuingTheFirstTurn() {
        for (int mapId = 1; mapId <= 21; mapId++) {
            for (int count : new int[]{2, 8}) {
                verifyMap(mapId, count, RoomMode.ITEM);
            }
        }
    }

    @Test
    void preservesPreviewForNoItemAndBothCaptainModes() {
        for (var mode : new RoomMode[]{RoomMode.NO_ITEM, RoomMode.CAPTAIN_ITEM, RoomMode.CAPTAIN_NO_ITEM}) {
            verifyMap(18, 4, mode);
        }
    }

    private void verifyMap(int mapId, int count, RoomMode mode) {
        try (var f = new MatchRoomFixture(new RoomScenario(6), count, mode); var logs = new LogCapture()) {
            var scenario = f.scenario;
            scenario.rooms.changeMap(f.owner, mapId);
            var outputs = f.players.stream().map(
                id -> {
                    var output = new TestMatchOutput();
                    scenario.matches.subscribe(id, output);
                    return output;
                }
            ).toList();

            if (mode.requiresCaptainSelection()) {
                f.captains();
            }

            assertEquals(RoomStartOutcome.STARTED, scenario.start.execute(f.owner).outcome());
            var token = scenario.rooms.matchContext(f.owner).token();
            var loading = scenario.matches.current(token).orElseThrow();

            assertEquals(mapId, loading.definitions().map().mapId());
            assertTrue(loading.turn().issuedAtNanos().isEmpty());
            assertEquals(count, loading.turn().order().size());
            assertEquals(loading.firstTurnSlot(), loading.turn().order().getFirst());
            assertEquals(RoomState.MATCH, scenario.rooms.current(f.owner).orElseThrow().state());

            var wire = new CollectingPacketOutput();
            new ProtocolMatchView(wire).start(loading);
            var start = wire.batches.getFirst().getLast();
            assertEquals(mapId, Short.toUnsignedInt(ByteBuffer.wrap(start).order(ByteOrder.LITTLE_ENDIAN).getShort(4)));

            scenario.matchTimers.now += 123;

            for (var member : loading.participants()) {
                var context = scenario.rooms.matchContext(member.sessionId());
                scenario.matches.loadingDone(
                    context,
                    member.slotIndex(),
                    member.spawn().settledX(),
                    member.spawn().settledY()
                ).toCompletableFuture().join();
            }

            var ready = scenario.matches.current(token).orElseThrow();
            assertEquals(MatchPhase.PREVIEW, ready.phase());
            assertEquals(scenario.matchTimers.now, ready.turn().issuedAtNanos().orElseThrow());
            assertEquals(1, ready.turn().revision());
            assertEquals(0, ready.turn().energyGrant());
            assertEquals(0, ready.turn().windRaw());
            assertEquals(0, ready.turn().boxAttemptBudget());
            assertEquals(count, ready.actors().size());
            scenario.matchTimers.now += 321;

            for (var member : ready.participants()) {
                var before = ready.actors().get(member.sessionId());
                var context = scenario.rooms.matchContext(member.sessionId());
                scenario.matches.loadingDone(context, member.slotIndex(), before.x(), before.y())
                    .toCompletableFuture().join();
                var after = scenario.matches.current(token).orElseThrow();

                assertSame(before, after.actors().get(member.sessionId()));
                assertSame(ready.turn(), after.turn());
                assertEquals(member.maxHp(), before.hp());
                assertEquals(0, before.energy());
            }

            assertEquals(RoomStartOutcome.ALREADY_STARTED, scenario.start.execute(f.owner).outcome());
            assertTrue(outputs.stream().allMatch(output -> output.starts.size() == 1 && output.turns.size() == 1));
            assertEquals(1, logs.events().stream().filter(
                event -> event.getKeyValuePairs().stream().anyMatch(
                    pair -> pair.value.equals("match.first_turn.issued")
                )
            ).count());
            assertFalse(logs.text().contains("credential"));
            assertFalse(logs.text().contains("Hero"));

            scenario.rooms.terminateMatch(token, RoomMatchEndReason.GIVE_UP).delivery().toCompletableFuture().join();

            assertTrue(scenario.matches.current(token).isEmpty());
            assertEquals(RoomState.WAITING, scenario.rooms.current(f.owner).orElseThrow().state());
            assertTrue(scenario.matchTimers.deadlines.isEmpty());
        }
    }
}
