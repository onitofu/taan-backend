package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.model.InventoryId;
import taan.online.inventory.model.InventoryItem;
import taan.online.inventory.model.InventoryItemId;
import taan.online.inventory.model.InventoryKind;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomSlotState;

class RoomInventoryContextTest {
    @Test
    void refreshesOnlyTheCurrentRoomsSnapshotsAndPreservesOtherRuntimeFields() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var observer = scenario.player(3, 1);
            var foreign = scenario.player(4, 2);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            var c = new RecordingRoomOutput();
            var d = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            scenario.enter.execute(observer, 1, "", c);
            scenario.create.execute(foreign, RoomScenario.command(), d);
            scenario.rooms.changeWeapon(guest, 2, 0);
            scenario.rooms.setReady(observer, true);
            var original = scenario.rooms.current(guest).orElseThrow();
            var access = new RoomInventoryMoveContextAccess(scenario.rooms);
            var actor = actor(guest, 2);
            var context = access.capture(actor);
            int updates = a.updates.size();
            var listings = scenario.rooms.forChannel(1);
            access.equipmentChanged(context, equipment());
            var changed = scenario.rooms.current(guest).orElseThrow();

            assertEquals(updates + 1, a.updates.size());
            assertEquals(changed, b.updates.getLast());
            assertEquals(changed, c.updates.getLast());
            assertTrue(d.updates.isEmpty());
            assertEquals(original.slots().get(1), changed.slots().get(1));
            assertEquals(RoomSlotState.READY, changed.slots().get(1).state());
            assertEquals(0, changed.slots().get(2).participant().weaponId());
            assertEquals(original.settings(), changed.settings());
            assertEquals(original.ownerSessionId(), changed.ownerSessionId());
            assertEquals(listings, scenario.rooms.forChannel(1));
            var part = changed.slots().get(2).participant().character().appearance().parts().get(1);
            assertEquals(1039, part.itemId());
            assertEquals(303, part.palette1Id());
            assertEquals(261, part.palette2Id());
            assertEquals(65535, part.palette3Id());
            access.equipmentChanged(context, equipment());

            assertEquals(updates + 1, a.updates.size());
            assertTrue(logs.text().contains("room.appearance.updated"));
            assertFalse(logs.text().contains("Hero2"));
        }
    }

    @Test
    void capturesReadinessIdentityChannelAndLifecycleWithoutKeepingStaleAdmissions() throws Exception {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var access = new RoomInventoryMoveContextAccess(scenario.rooms);
            var actor = actor(guest, 2);
            var outside = access.capture(actor);

            assertTrue(outside.equipmentChangesAllowed());
            assertNull(outside.roomAdmissionId());
            access.equipmentChanged(outside, equipment());
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            var context = access.capture(actor);

            assertFalse(access.current(outside));
            assertTrue(access.current(context));
            assertFalse(access.capture(new InventoryActor(guest, new AccountId(99), 2, actor.gender()))
                .equipmentChangesAllowed());
            assertFalse(access.capture(new InventoryActor(guest, actor.accountId(), 99, actor.gender()))
                .equipmentChangesAllowed());
            assertFalse(access.capture(new InventoryActor(guest, actor.accountId(), 2, AccountGender.MALE))
                .equipmentChangesAllowed());
            scenario.rooms.setReady(guest, true);
            var ready = access.capture(actor);
            access.equipmentChanged(ready, equipment());

            assertFalse(ready.equipmentChangesAllowed());
            assertFalse(access.current(context));
            scenario.rooms.setReady(guest, false);
            scenario.leave.execute(guest, 1, 0);
            scenario.lobby(guest);
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            var before = scenario.rooms.current(guest).orElseThrow();
            access.equipmentChanged(context, equipment());

            assertEquals(before, scenario.rooms.current(guest).orElseThrow());
            assertFalse(access.current(context));
            scenario.channels.move(guest, new MoveToCommand(0, 2, 0, 929));
            assertFalse(access.capture(actor).equipmentChangesAllowed());
            scenario.channels.release(guest, "DISCONNECT");
            assertFalse(access.capture(actor).equipmentChangesAllowed());
            rooms(scenario.rooms).get(new RoomId(1)).close();
            assertFalse(access.capture(actor(owner, 1)).equipmentChangesAllowed());
            scenario.rooms.close();
            assertFalse(access.capture(actor).equipmentChangesAllowed());
        }
    }

    @Test
    void closesTheActorOnProjectionFailureAndKeepsCommittedAppearanceWhenPeerDeliveryFails() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            var access = new RoomInventoryMoveContextAccess(scenario.rooms);
            a.onUpdate = () -> {
                throw new IllegalStateException("hidden@example.test");
            };
            access.equipmentChanged(access.capture(actor(guest, 2)), equipment());

            assertEquals("ROOM_NOTIFICATION_FAILED", a.closed);
            assertEquals(1039, b.updates.getLast().slots().get(2).participant().character().appearance()
                .parts().get(1).itemId());
            access.equipmentChanged(
                access.capture(actor(guest, 2)),
                new CharacterInventory(new InventoryId(100), InventoryKind.BAG, List.of())
            );

            assertEquals("ROOM_APPEARANCE_FAILED", b.closed);
            assertTrue(logs.text().contains("room.appearance.failed"));
            assertTrue(logs.text().contains("room.notification.failed"));
            assertFalse(logs.text().contains("hidden@example.test"));
        }
    }

    static InventoryActor actor(UUID session, long id) {
        return new InventoryActor(session, new AccountId(id), id, AccountGender.FEMALE);
    }

    static CharacterInventory equipment() {
        return new CharacterInventory(
            new InventoryId(100),
            InventoryKind.EQUIPMENT,
            List.of(new InventoryItem(new InventoryItemId(123), 1, 1039, 1, 303, 261, 65535))
        );
    }

    private Map<RoomId, Room> rooms(RoomRegistry registry) throws Exception {
        var field = RoomRegistry.class.getDeclaredField("rooms");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var rooms = (Map<RoomId, Room>) field.get(registry);
        return rooms;
    }
}
