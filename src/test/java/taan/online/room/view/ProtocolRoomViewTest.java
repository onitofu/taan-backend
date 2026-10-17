package taan.online.room.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static taan.online.RoomFixtures.root;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.RoomFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.AppearancePart;
import taan.online.character.model.CharacterAppearance;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;
import taan.online.character.model.GameCharacter;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomSlot;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomTitle;
import taan.online.room.service.model.RoomSnapshot;

class ProtocolRoomViewTest {
    @Test
    void matchesIndependentGoldenRosterJoinRemovalAndOwnerTransferInSingleBatches() throws Exception {
        var a = participant(1, "ProbeHero");
        var b = participant(2, "GuestHero");
        var settings = new RoomSettings(new RoomTitle("Ngon vào đây ! "), RoomMode.ITEM, RoomRule.BONUS, 1, 8);
        var room = new Room(new RoomId(1), UUID.randomUUID(), 1, settings, null, a);
        room.enter(b, "");
        var output = new CollectingPacketOutput();
        var view = new ProtocolRoomView(output);
        view.initialize(snapshot(room));

        assertEquals(1, output.batches.size());
        assertArrayEquals(root("PcGameSession_Init-two-players"), output.batches.getFirst().getFirst());
        view.update(snapshot(room), List.of(2), false);

        assertArrayEquals(root("PcGameSession_UpdateSlot-join-red2"), output.batches.getLast().getFirst());
        room.leave(a.sessionId());
        view.update(snapshot(room), List.of(0), true);

        assertEquals(2, output.batches.getLast().size());
        assertArrayEquals(root("PcGameSession_UpdateSlot-leave-blue0"), output.batches.getLast().getFirst());
        assertArrayEquals(root("owner-transfer"), output.batches.getLast().get(1));
        assertEquals(1, new RoomSnapshotMapper().slot(new RoomSlot(7, RoomSlotState.CLOSED, null)).state());
        view.close("TEST_CLOSE");

        assertEquals(List.of("TEST_CLOSE"), output.closeReasons);
    }

    static RoomSnapshot snapshot(Room room) {
        return new RoomSnapshot(
            room.id(),
            room.generation(),
            room.channelId(),
            room.settings(),
            room.ownerSessionId(),
            room.slots(),
            false
        );
    }

    private RoomParticipant participant(long id, String name) {
        var parts = RoomFixtures.appearance().parts();
        var appearance = new CharacterAppearance(
            IntStream.range(0, 16).mapToObj(
                index -> {
                    var part = parts.get(index);
                    return new AppearancePart(
                        index,
                        part.itemId(),
                        part.palette1Id(),
                        part.palette2Id(),
                        part.palette3Id()
                    );
                }
            ).toList()
        );
        var character = new GameCharacter(
            new CharacterId(1000 + id),
            new AccountId(id),
            new CharacterName(name),
            1,
            AccountGender.MALE,
            appearance,
            Instant.EPOCH
        );
        return new RoomParticipant(UUID.randomUUID(), character, 3);
    }
}
