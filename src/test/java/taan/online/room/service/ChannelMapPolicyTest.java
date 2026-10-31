package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.InventoryFixtures;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.bootstrap.RoomDefinitionLoader;
import taan.online.channel.service.model.ChannelMembershipSnapshot;
import taan.online.room.controller.RoomSettingsMapper;
import taan.online.room.model.Room;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomTitle;
import taan.online.room.service.model.ChangeRoomSettingsOutcome;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.room.service.model.RoomMatchToken;

class ChannelMapPolicyTest {
    @Test
    void usesIndependentChannelKindsForCreationSelectionAndCorrectiveReplies() {
        for (int kind : new int[]{0, 1, 4, 6}) {
            try (var f = new RoomScenario(kind); var logs = new LogCapture()) {
                var owner = f.player(1, 1);
                var created = f.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());

                assertEquals(CreateRoomOutcome.CREATED, created.outcome());
                assertEquals(kind == 4 ? 3 : 1, created.room().settings().mapId());
                assertEquals(0, f.channels.catalog().directory().groups().getFirst().type());
                assertEquals(kind, f.channels.membership(owner).orElseThrow().kind());
                assertEquals(0, f.channels.kind(2));

                for (int mapId = 1; mapId <= 21; mapId++) {
                    boolean permitted = MatchFixtures.catalog().maps().get(mapId).channelKinds().contains(kind);
                    int previous = f.rooms.current(owner).orElseThrow().settings().mapId();
                    var result = f.rooms.changeMap(owner, mapId);

                    assertEquals(permitted ? mapId : previous, result.settings().mapId());
                    assertEquals(!permitted, result.outcome() == ChangeRoomSettingsOutcome.REJECTED);
                    var combined = f.rooms.changeSettings(owner, RoomSettingsMapper.settings(mapId, 0, 2));
                    assertEquals(permitted, combined.outcome() != ChangeRoomSettingsOutcome.REJECTED);
                }

                assertTrue(logs.text().contains("channelKind=\"" + kind + "\""));
                assertFalse(logs.text().contains("Hero"));
                assertFalse(logs.text().contains("credential"));
            }
        }
    }

    @Test
    void rejectsForgedKindAndForbiddenCreationEvenThroughTheRegistry() {
        try (var f = new RoomScenario(0)) {
            var owner = f.player(1, 1);
            var context = f.channels.membership(owner).orElseThrow();
            var character = new RoomCharacterLoader(f.characters, InventoryFixtures.catalog())
                .load(context).orElseThrow();
            var actor = new RoomParticipant(owner, character, 0);
            var settings = new RoomSettings(new RoomTitle("Public"), RoomMode.ITEM, RoomRule.BONUS, 3, 8);
            var output = new RecordingRoomOutput();
            var forged = new ChannelMembershipSnapshot(context.channelId(), context.phase(), context.participant(), 6);

            assertEquals("CHANNEL_CHANGED", f.rooms.create(forged, settings, null, actor, output).reason());
            assertEquals("UNSUPPORTED_MAP", f.rooms.create(context, settings, null, actor, output).reason());
            assertTrue(f.rooms.current(owner).isEmpty());
        }
    }

    @Test
    void rechecksTheMapAtReservationAndBeforeAnAlreadyReservedStart() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var scenario = f.scenario;
            var snapshot = scenario.rooms.current(f.owner).orElseThrow();
            var owner = snapshot.slots().getFirst().participant();
            var room = new Room(snapshot.id(), snapshot.generation(), 1, snapshot.settings(), null, owner);
            var subscriptions = new HashMap<UUID, RoomSubscription>();
            subscriptions.put(f.owner, RoomSubscription.create(room, owner, new RecordingRoomOutput()));

            for (var slot : snapshot.slots()) {
                if (slot.participant() != null && !slot.participant().sessionId().equals(f.owner)) {
                    var actor = slot.participant();
                    room.enter(actor, "");
                    room.setReady(actor.character().accountId(), true);
                    subscriptions.put(
                        actor.sessionId(),
                        RoomSubscription.create(room, actor, new RecordingRoomOutput())
                    );
                }
            }

            var maps = new RoomDefinitionLoader().maps(MatchFixtures.catalog());
            room.changeSettings(RoomMode.ITEM, RoomRule.BONUS, 3, maps);
            var operations = new RoomMatchOperations(
                new Object(),
                scenario.channels,
                Map.of(room.id(), room),
                subscriptions,
                null,
                scenario.matches,
                ignored -> { },
                maps
            );

            assertEquals("UNSUPPORTED_MAP", operations.reserve(f.owner, null).reason());
            room.reserveMatch(UUID.randomUUID());
            var token = new RoomMatchToken(room.id(), room.generation(), room.matchId());

            assertEquals("UNSUPPORTED_MAP", operations.startRejection(token));
        }
    }
}
