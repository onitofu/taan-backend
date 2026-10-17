package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.room.model.RoomSlotState;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.session.model.SessionId;

class EnterRoomActionTest {
    @Test
    void publishesOneRosterBalancesTeamsAndTransfersOwnershipWithoutMovingTheSuccessor() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var a = scenario.player(1, 1);
            var b = scenario.player(2, 1);
            var c = scenario.player(3, 1);
            var outA = new RecordingRoomOutput();
            var outB = new RecordingRoomOutput();
            var outC = new RecordingRoomOutput();
            int room = scenario.create.execute(a, RoomScenario.command(), outA).room().id().value();
            scenario.characters.onCommit = () -> assertFalse(Thread.holdsLock(scenario.rooms));

            assertEquals(EnterRoomOutcome.ENTERED, scenario.enter.execute(b, room, outB).outcome());
            assertEquals(2, outB.initializations.getFirst().slots().get(2).participant().character().id().value());
            assertEquals(List.of(2), outA.changedSlots.getFirst());
            assertEquals(2, scenario.rooms.forChannel(1).getFirst().playerCount());
            assertEquals(EnterRoomOutcome.ENTERED, scenario.enter.execute(c, room, outC).outcome());
            assertEquals(c, outC.initializations.getFirst().slots().get(1).participant().sessionId());
            assertEquals(1, outA.initializations.size());
            assertEquals(2, outA.updates.size());
            var old = scenario.rooms.membership(b).orElseThrow();
            scenario.leave.execute(b, room, 0);

            assertEquals(RoomSlotState.OPEN, outA.updates.getLast().slots().get(2).state());
            assertFalse(outA.ownerChanges.getLast());
            scenario.lobby(b);
            assertEquals(EnterRoomOutcome.ENTERED, scenario.enter.execute(b, room, outB).outcome());
            var current = scenario.rooms.membership(b).orElseThrow();
            scenario.rooms.release(old, "OLD_DELIVERY");

            assertNotEquals(old.admissionId(), current.admissionId());
            assertEquals(current, scenario.rooms.membership(b).orElseThrow());
            scenario.rooms.release(a, "PEER_CLOSED");

            assertEquals(c, scenario.rooms.current(c).orElseThrow().ownerSessionId());
            assertTrue(outC.ownerChanges.getLast());
            assertEquals(c, outC.updates.getLast().slots().get(1).participant().sessionId());
            scenario.rooms.release(c, "PEER_CLOSED");

            assertEquals(b, scenario.rooms.current(b).orElseThrow().ownerSessionId());
            scenario.rooms.release(b, "PEER_CLOSED");

            assertTrue(scenario.rooms.forChannel(1).isEmpty());
            assertTrue(logs.text().contains("room.owner.changed"));
            assertFalse(logs.text().contains("Hero1"));
        }
    }

    @Test
    void rejectsInvalidMembershipChannelAndMissingCharactersWithoutPublishing() {
        try (var scenario = new RoomScenario()) {
            var a = scenario.player(1, 1);
            var b = scenario.player(2, 0);
            var output = new RecordingRoomOutput();
            int room = scenario.create.execute(a, RoomScenario.command(), output).room().id().value();

            assertEquals(EnterRoomOutcome.IGNORED, scenario.enter.execute(UUID.randomUUID(), room, output).outcome());
            assertEquals(EnterRoomOutcome.ALREADY_IN_ROOM, scenario.enter.execute(a, room, output).outcome());
            assertEquals("NOT_IN_LOBBY", scenario.enter.execute(b, room, output).reason());
            scenario.channels.move(b, new MoveToCommand(0, 1, 0, 929));

            assertEquals("NOT_IN_LOBBY", scenario.enter.execute(b, room, output).reason());
            scenario.lobby(b);
            assertEquals(EnterRoomOutcome.NOT_FOUND, scenario.enter.execute(b, 65535, output).outcome());
            scenario.channels.move(b, new MoveToCommand(0, 2, 0, 929));
            scenario.lobby(b);

            assertEquals(EnterRoomOutcome.NOT_FOUND, scenario.enter.execute(b, room, output).outcome());
            scenario.channels.move(b, new MoveToCommand(0, 1, 0, 929));
            scenario.lobby(b);
            scenario.characters.stored.removeIf(value -> value.id().value() == 2);

            assertEquals("CHARACTER_UNAVAILABLE", scenario.enter.execute(b, room, output).reason());
            assertEquals(1, output.initializations.size());
            assertTrue(scenario.rooms.membership(b).isEmpty());
        }
    }

    @Test
    void revalidatesAfterDatabaseWorkAndRollsBackFailedReads() {
        for (String mode : List.of("CLOSE", "MOVE", "REMOVE", "STOP", "FAIL")) {
            try (var scenario = new RoomScenario()) {
                var a = scenario.player(1, 1);
                var b = scenario.player(2, 1);
                int room = scenario.create.execute(
                    a,
                    RoomScenario.command(),
                    new RecordingRoomOutput()
                ).room().id().value();
                var out = new RecordingRoomOutput();
                scenario.characters.onCommit = () -> {
                    switch (mode) {
                        case "CLOSE" -> scenario.sessions.close(new SessionId(b));
                        case "MOVE" -> scenario.channels.move(b, new MoveToCommand(0, 2, 0, 929));
                        case "REMOVE" -> scenario.rooms.release(a, "LEFT");
                        case "STOP" -> scenario.rooms.close();
                        default -> throw new IllegalStateException("Private database value");
                    }
                };

                if (mode.equals("FAIL")) {
                    assertThrows(IllegalStateException.class, () -> scenario.enter.execute(b, room, out));
                    assertEquals(1, scenario.characters.rollbacks);
                } else {
                    var result = scenario.enter.execute(b, room, out);
                    assertEquals(
                        switch (mode) {
                            case "CLOSE", "STOP" -> EnterRoomOutcome.IGNORED;
                            case "REMOVE" -> EnterRoomOutcome.NOT_FOUND;
                            default -> EnterRoomOutcome.REJECTED;
                        },
                        result.outcome()
                    );
                }

                assertTrue(out.initializations.isEmpty());
                assertTrue(scenario.rooms.membership(b).isEmpty());
            }
        }
    }
}
