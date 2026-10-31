package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomEntryFixtures;
import taan.online.RoomListingFixtures;
import taan.online.RoomScenario;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.lobby.view.LobbyRoomListMapper;
import taan.online.room.controller.RoomReplyOutcome;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.service.model.CreateRoomCommand;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.view.RoomSnapshotMapper;

class RoomPasswordAdmissionTest {
    @Test
    void acceptsProtectedCreationAndPublishesOnlyPresenceInBothRoomAndLobbySnapshots() throws Exception {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var response = scenario.handle(owner, PqGameSessionCreate.TYPE_ID, root("create-password"));
            var wire = new RoomResponseCodec();
            byte[] initial = scenario.lastOutput.batches.getFirst().getFirst();
            var init = wire.initialize(initial);
            var listing = scenario.rooms.forChannel(1).getFirst();
            var snapshot = scenario.rooms.current(owner).orElseThrow();

            assertEquals(RoomReplyOutcome.CREATED, response.outcome());
            assertTrue(snapshot.protectedRoom());
            assertTrue(listing.protectedRoom());
            assertEquals("", init.info().details().password());
            assertEquals("", new LobbyRoomListMapper().map(listing).password());
            assertFalse(snapshot.toString().contains("пароль"));
            assertFalse(listing.toString().contains("пароль"));
            assertNoSecret(initial, "пароль");

            for (String password : List.of("", "Пароль", "пароль ", "x".repeat(11))) {
                var before = scenario.rooms.current(owner).orElseThrow();
                var listings = scenario.rooms.forChannel(1);
                var result = scenario.handle(
                    guest,
                    PqGameSessionEnter.TYPE_ID,
                    RoomEntryFixtures.enter(init.info().roomId(), password)
                );

                assertEquals(RoomReplyOutcome.ENTRY_REJECTED, result.outcome());
                assertArrayEquals(root("enter-fail-6"), scenario.lastOutput.batches.getFirst().getFirst());
                assertEquals(before, scenario.rooms.current(owner).orElseThrow());
                assertEquals(listings, scenario.rooms.forChannel(1));
                assertTrue(scenario.rooms.membership(guest).isEmpty());
                assertEquals(ChannelPresencePhase.LOBBY, scenario.channels.membership(guest).orElseThrow().phase());
                assertEquals(1, scenario.outputs.get(owner).batches.size());
            }

            var result = scenario.handle(
                guest,
                PqGameSessionEnter.TYPE_ID,
                RoomEntryFixtures.enter(init.info().roomId(), "пароль")
            );
            byte[] accepted = scenario.lastOutput.batches.getFirst().getFirst();

            assertEquals(RoomReplyOutcome.ENTERED, result.outcome());
            assertEquals(2, wire.initialize(accepted).info().details().playerCount());
            assertEquals("", wire.initialize(accepted).info().details().password());
            assertNoSecret(accepted, "пароль");
            assertTrue(logs.text().contains("WRONG_PASSWORD"));
            assertFalse(logs.text().contains("пароль"));
            assertFalse(logs.text().contains("Комната"));
        }
    }

    @Test
    void rejectsInvalidCreationBeforeReadingCharactersAndKeepsTitlesAndPasswordsOutOfErrors() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);

            for (String password : List.of("", "Private1234", "😀".repeat(5) + "x", "\ud800", "\udfff")) {
                var command = command("Private room", password);
                var result = scenario.create.execute(owner, command, new RecordingRoomOutput());

                assertEquals(CreateRoomOutcome.REJECTED, result.outcome());
                assertEquals("INVALID_PASSWORD", result.reason());
                assertFalse(command.toString().contains("Private"));
                assertTrue(scenario.rooms.forChannel(1).isEmpty());
            }

            assertEquals(0, scenario.characters.transactions);
            assertFalse(logs.text().contains("Private"));
            assertTrue(logs.text().contains("INVALID_PASSWORD"));
        }
    }

    @Test
    void enforcesUiLengthsAsBackendPolicyAndKeepsSpacesAndSupplementaryCharacters() {
        for (String password : List.of("x".repeat(10), "😀".repeat(5), " ".repeat(10), " Ключ ")) {
            try (var scenario = new RoomScenario()) {
                var owner = scenario.player(1, 1);
                var guest = scenario.player(2, 1);
                var created = scenario.create.execute(
                    owner,
                    command("😀".repeat(128), password),
                    new RecordingRoomOutput()
                );
                int room = created.room().id().value();

                assertEquals(CreateRoomOutcome.CREATED, created.outcome());
                assertEquals(256, created.room().settings().title().value().length());
                assertEquals(
                    EnterRoomOutcome.ENTERED,
                    scenario.enter.execute(guest, room, password, new RecordingRoomOutput()).outcome()
                );
            }
        }

        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var result = scenario.create.execute(
                owner,
                command("😀".repeat(128) + "x", "secret"),
                new RecordingRoomOutput()
            );

            assertEquals("INVALID_TITLE", result.reason());
            assertEquals(0, scenario.characters.transactions);
        }
    }

    @Test
    void returnsGenericCreateFailureForInvalidPasswordAndIgnoresTheEnterFieldForAnOpenRoom() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var codec = new RoomResponseCodec();
            var failed = scenario.handle(owner, PqGameSessionCreate.TYPE_ID, RoomListingFixtures.create("Room", ""));

            assertEquals(RoomReplyOutcome.CREATE_REJECTED, failed.outcome());
            assertEquals(0, codec.createFail(scenario.lastOutput.batches.getFirst().getFirst()).reason());
            var open = scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());

            assertFalse(open.room().protectedRoom());
            assertEquals(
                EnterRoomOutcome.ENTERED,
                scenario.enter.execute(guest, open.room().id().value(), "ignored".repeat(10), new RecordingRoomOutput())
                    .outcome()
            );
        }
    }

    @Test
    void rechecksRemovalAndCapacityAfterTheCharacterReadWithoutReservingThePromptSlot() {
        for (boolean removed : new boolean[]{true, false}) {
            try (var scenario = new RoomScenario()) {
                var owner = scenario.player(1, 1);
                int room = scenario.create.execute(
                    owner,
                    command("Room", "secret"),
                    new RecordingRoomOutput()
                ).room().id().value();

                for (int i = 2; i <= 7; i++) {
                    var guest = scenario.player(i, 1);
                    scenario.enter.execute(guest, room, "secret", new RecordingRoomOutput());
                }

                var contender = scenario.player(8, 1);
                var other = scenario.player(9, 1);
                var context = scenario.channels.membership(other).orElseThrow();
                var target = scenario.rooms.entryTarget(context, room).orElseThrow();
                scenario.characters.onCommit = () -> {
                    if (removed) {
                        var members = scenario.rooms.current(owner).orElseThrow().slots().stream()
                            .filter(slot -> slot.participant() != null)
                            .map(slot -> slot.participant().sessionId()).toList();
                        members.forEach(id -> scenario.rooms.release(id, "LEFT"));
                    } else {
                        scenario.rooms.enter(
                            context,
                            target,
                            scenario.owner(other),
                            "secret",
                            new RecordingRoomOutput()
                        );
                    }
                };
                var output = new RecordingRoomOutput();
                var result = scenario.enter.execute(contender, room, "secret", output);

                assertEquals(removed ? EnterRoomOutcome.NOT_FOUND : EnterRoomOutcome.FULL, result.outcome());
                assertTrue(output.initializations.isEmpty());
                assertTrue(scenario.rooms.membership(contender).isEmpty());
            }
        }
    }

    @Test
    void rejectsAClosedRoomAndCrossChannelGuessBeforeComparingPasswords() throws Exception {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var foreign = scenario.player(3, 2);
            var snapshot = scenario.create.execute(owner, command("Room", "secret"), new RecordingRoomOutput()).room();
            int room = snapshot.id().value();
            var foreignReply = scenario.handle(
                foreign,
                PqGameSessionEnter.TYPE_ID,
                RoomEntryFixtures.enter(room, "bad")
            );

            assertEquals(RoomReplyOutcome.ENTRY_REJECTED, foreignReply.outcome());
            assertArrayEquals(root("enter-fail-4"), scenario.lastOutput.batches.getFirst().getFirst());
            var field = RoomRegistry.class.getDeclaredField("rooms");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var rooms = (Map<RoomId, Room>) field.get(scenario.rooms);
            rooms.get(snapshot.id()).close();
            scenario.handle(guest, PqGameSessionEnter.TYPE_ID, RoomEntryFixtures.enter(room, "secret"));

            assertArrayEquals(root("enter-fail-4"), scenario.lastOutput.batches.getFirst().getFirst());
            assertTrue(scenario.rooms.membership(guest).isEmpty());
        }
    }

    @Test
    void keepsTheSecretDuringOwnerHandoffAndDoesNotLeakItThroughNotificationFailures() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var newcomer = scenario.player(3, 1);
            var output = new RecordingRoomOutput();
            var created = scenario.create.execute(owner, command("Room", "secret"), output);
            int room = created.room().id().value();
            output.onUpdate = () -> {
                throw new IllegalStateException("secret");
            };
            scenario.enter.execute(guest, room, "secret", new RecordingRoomOutput());
            scenario.rooms.release(owner, "PEER_CLOSED");
            var snapshot = scenario.rooms.current(guest).orElseThrow();

            assertEquals(guest, snapshot.ownerSessionId());
            assertTrue(snapshot.protectedRoom());
            assertEquals("", new RoomSnapshotMapper().map(snapshot).info().details().password());
            assertEquals(
                EnterRoomOutcome.WRONG_PASSWORD,
                scenario.enter.execute(newcomer, room, "", new RecordingRoomOutput()).outcome()
            );
            assertTrue(logs.text().contains("room.notification.failed"));
            assertFalse(logs.text().contains("secret"));
            scenario.rooms.release(guest, "PEER_CLOSED");

            assertTrue(scenario.rooms.forChannel(1).isEmpty());
        }
    }

    private static CreateRoomCommand command(String title, String password) {
        return RoomScenario.command(5, 0, title, 0, 0, 2, 0, 8, password, 0);
    }

    private void assertNoSecret(byte[] root, String password) {
        String secret = HexFormat.of().formatHex(password.getBytes(StandardCharsets.UTF_16LE));
        assertFalse(HexFormat.of().formatHex(root).contains(secret));
    }
}
