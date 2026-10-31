package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.session.model.SessionId;

class QuickJoinRoomActionTest {
    @Test
    void prefersTheMostPopulatedRoomThenTheSmallestIdAndLoadsAppearanceOnce() {
        try (var scenario = new RoomScenario()) {
            var first = scenario.player(1, 1);
            var second = scenario.player(2, 1);
            var third = scenario.player(3, 1);
            var joiner = scenario.player(4, 1);
            var firstRoom = scenario.create.execute(first, RoomScenario.command(), new RecordingRoomOutput()).room();
            var ownerOutput = new RecordingRoomOutput();
            var secondRoom = scenario.create.execute(
                second,
                RoomScenario.command(5, 0, "No items", 0, 1, 0, 0, 8, null, 0),
                ownerOutput
            ).room();
            var output = new RecordingRoomOutput();
            scenario.enter.execute(third, secondRoom.id().value(), "", new RecordingRoomOutput());
            int reads = scenario.characters.transactions;
            var result = scenario.quickJoin.execute(joiner, 65535, output);
            var joined = output.initializations.getFirst();

            assertEquals(EnterRoomOutcome.ENTERED, result.outcome());
            assertEquals(secondRoom.id(), joined.id());
            assertEquals(3, scenario.rooms.forChannel(1).get(1).playerCount());
            assertEquals(1, scenario.rooms.forChannel(1).getFirst().playerCount());
            assertEquals(1, joined.slots().get(1).index());
            assertEquals(joiner, joined.slots().get(1).participant().sessionId());
            assertEquals(1, ownerOutput.initializations.size());
            assertEquals(List.of(1), ownerOutput.changedSlots.getLast());
            assertEquals(reads + 1, scenario.characters.transactions);
            assertEquals(0, joined.slots().get(1).participant().weaponId());
            scenario.rooms.release(joiner, "LEFT");
            scenario.rooms.release(third, "LEFT");
            scenario.lobby(joiner);
            var retry = new RecordingRoomOutput();
            scenario.quickJoin.execute(joiner, 65535, retry);

            assertEquals(firstRoom.id(), retry.initializations.getFirst().id());
        }
    }

    @Test
    void rejectsUnknownSelectorsInvalidPhasesMissingCharactersAndDuplicateMemberships() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var id = scenario.player(1, 0);
            var output = new RecordingRoomOutput();

            assertEquals(
                EnterRoomOutcome.IGNORED,
                scenario.quickJoin.execute(UUID.randomUUID(), 65535, output).outcome()
            );

            for (int selector : new int[]{0, 1, 65534}) {
                assertEquals("UNSUPPORTED_SELECTOR", scenario.quickJoin.execute(id, selector, output).reason());
            }

            assertEquals("NOT_IN_LOBBY", scenario.quickJoin.execute(id, 65535, output).reason());
            scenario.channels.move(id, new MoveToCommand(0, 1, 0, 929));

            assertEquals("NOT_IN_LOBBY", scenario.quickJoin.execute(id, 65535, output).reason());
            assertEquals(0, scenario.characters.transactions);
            scenario.lobby(id);

            assertEquals("NO_MATCHING_ROOM", scenario.quickJoin.execute(id, 65535, output).reason());
            var character = scenario.characters.stored.removeFirst();

            assertEquals("CHARACTER_UNAVAILABLE", scenario.quickJoin.execute(id, 65535, output).reason());
            scenario.characters.stored.add(character);
            scenario.create.execute(id, RoomScenario.command(), new RecordingRoomOutput());
            int reads = scenario.characters.transactions;

            assertEquals(EnterRoomOutcome.ALREADY_IN_ROOM, scenario.quickJoin.execute(id, 65535, output).outcome());
            assertEquals(reads, scenario.characters.transactions);
            assertTrue(output.initializations.isEmpty());
            assertTrue(logs.text().contains("room.quick_join.rejected"));
            assertTrue(logs.text().contains("channelId"));
            assertFalse(logs.text().contains("Hero1"));
        }
    }

    @Test
    void revalidatesTheContextAfterTheReadAndRollsBackFailuresWithoutPublishing() {
        for (String mode : List.of("CLOSE", "MOVE", "REMOVE", "STOP", "FAIL")) {
            try (var scenario = new RoomScenario()) {
                var owner = scenario.player(1, 1);
                var guest = scenario.player(2, 1);
                scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
                var output = new RecordingRoomOutput();
                scenario.characters.onCommit = () -> {
                    assertFalse(Thread.holdsLock(scenario.rooms));

                    switch (mode) {
                        case "CLOSE" -> scenario.sessions.close(new SessionId(guest));
                        case "MOVE" -> scenario.channels.move(guest, new MoveToCommand(0, 2, 0, 929));
                        case "REMOVE" -> scenario.rooms.release(owner, "LEFT");
                        case "STOP" -> scenario.rooms.close();
                        default -> throw new IllegalStateException("Private database value");
                    }
                };

                if (mode.equals("FAIL")) {
                    assertThrows(IllegalStateException.class, () -> scenario.quickJoin.execute(guest, 65535, output));
                    assertEquals(1, scenario.characters.rollbacks);
                } else {
                    var result = scenario.quickJoin.execute(guest, 65535, output);

                    assertEquals(
                        switch (mode) {
                            case "CLOSE", "STOP" -> EnterRoomOutcome.IGNORED;
                            case "REMOVE" -> EnterRoomOutcome.NOT_FOUND;
                            default -> EnterRoomOutcome.REJECTED;
                        },
                        result.outcome()
                    );
                }

                assertTrue(output.initializations.isEmpty());
                assertTrue(scenario.rooms.membership(guest).isEmpty());
            }
        }
    }

    @Test
    void choosesFromTheRoomsThatExistAfterLoadingInsteadOfUsingAnEarlierCandidate() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var old = scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput()).room();
            scenario.characters.onCommit = () -> {
                scenario.rooms.release(owner, "LEFT");
                scenario.lobby(owner);
                scenario.rooms.create(
                    scenario.channels.membership(owner).orElseThrow(),
                    RoomScenario.settings(),
                    null,
                    scenario.owner(owner),
                    new RecordingRoomOutput()
                );
            };
            var output = new RecordingRoomOutput();
            var result = scenario.quickJoin.execute(guest, 65535, output);

            assertEquals(EnterRoomOutcome.ENTERED, result.outcome());
            assertEquals(scenario.rooms.current(owner).orElseThrow().id(), output.initializations.getFirst().id());
            assertFalse(old.generation().equals(output.initializations.getFirst().generation()));
        }
    }
}
