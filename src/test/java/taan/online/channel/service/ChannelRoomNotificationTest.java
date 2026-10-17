package taan.online.channel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.SelectionFixtures;
import taan.online.channel.service.model.MoveToCommand;

class ChannelRoomNotificationTest {
    @Test
    void scopesUpdatesToLiveLobbySubscribersAndKeepsFailingRecipientsIndependent() {
        var access = new ChannelTestAccess();
        var good = access.add(1, 1);
        var broken = access.add(2, 1);
        var otherChannel = access.add(3, 1);
        var hidden = access.add(4, 1);
        var expired = access.add(5, 1);
        var registry = SelectionFixtures.registry(access);
        var a = new RecordingChannelOutput();
        var b = new RecordingChannelOutput();
        var c = new RecordingChannelOutput();
        var d = new RecordingChannelOutput();
        var e = new RecordingChannelOutput();
        registry.move(good, new MoveToCommand(0, 1, 0, 929));
        registry.move(broken, new MoveToCommand(0, 1, 0, 929));
        registry.move(otherChannel, new MoveToCommand(0, 2, 0, 929));
        registry.move(hidden, new MoveToCommand(0, 1, 0, 929));
        registry.move(expired, new MoveToCommand(0, 1, 0, 929));
        registry.enter(good, a);
        registry.enter(broken, b);
        registry.enter(otherChannel, c);
        registry.enter(hidden, d);
        registry.enter(expired, e);
        registry.hideFromLobby(hidden);
        access.players.remove(expired);
        b.onRooms = () -> {
            registry.hideFromLobby(broken);
            throw new IllegalArgumentException("Private title and password");
        };
        b.onClose = () -> {
            assertFalse(Thread.holdsLock(registry));
            registry.release(broken, "FAILED");
        };

        try (var logs = new LogCapture()) {
            registry.roomChanged(1, 7);
            registry.roomsReplaced(1);

            assertEquals(
                1,
                logs.events().stream().filter(
                    event -> event.getKeyValuePairs().stream()
                        .anyMatch(
                            field -> field.key.equals("event")
                                    && field.value.equals("channel.lobby_notification.failed")
                        )
                )
                    .count()
            );
            assertFalse(logs.text().contains("Private title and password"));
        }

        assertEquals(List.of(7), a.changedRooms);
        assertEquals(1, a.replacements);
        assertEquals("LOBBY_NOTIFICATION_FAILED", b.closeReason);
        assertTrue(c.changedRooms.isEmpty());
        assertEquals(0, c.replacements);
        assertTrue(d.changedRooms.isEmpty());
        assertEquals(0, d.replacements);
        assertTrue(e.changedRooms.isEmpty());
        assertEquals(0, e.replacements);
    }
}
