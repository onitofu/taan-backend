package taan.online.room.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.lobby.protocol.codec.LobbyRoomListCodec;
import taan.online.lobby.protocol.packet.PcLobbyInitRoomList;
import taan.online.lobby.view.LobbyRoomListMapper;
import taan.online.protocol.BinaryWriter;
import taan.online.room.protocol.codec.GameSessionInfoCodec;
import taan.online.room.service.model.CreateRoomCommand;

class RoomPasswordPresentationTest {
    @Test
    void encodesThePresenceFlagAndTheMandatoryEmptyWstringWithoutExportingTheSecret() {
        for (boolean protectedRoom : new boolean[]{true, false}) {
            try (var scenario = new RoomScenario()) {
                var owner = scenario.player(1, 1);
                var created = scenario.create.execute(
                    owner,
                    new CreateRoomCommand(5, 0, "Room", 0, 0, 2, 0, 8, protectedRoom ? "secret" : null, 0),
                    new RecordingRoomOutput()
                );
                var info = new RoomSnapshotMapper().map(created.room()).info();
                var listing = new LobbyRoomListMapper().map(scenario.rooms.forChannel(1).getFirst());
                var output = new BinaryWriter();
                new GameSessionInfoCodec().encode(output, info);
                byte[] encodedInfo = output.toByteArray();
                byte[] encodedList = new LobbyRoomListCodec().encode(new PcLobbyInitRoomList(List.of(listing)));

                if (protectedRoom) {
                    assertEquals("", info.details().password());
                    assertEquals("", listing.password());
                    assertArrayEquals(
                        HexFormat.of().parseHex("01000000000100"),
                        Arrays.copyOfRange(encodedInfo, encodedInfo.length - 7, encodedInfo.length)
                    );
                    assertArrayEquals(
                        HexFormat.of().parseHex("0100000000"),
                        Arrays.copyOfRange(encodedList, encodedList.length - 5, encodedList.length)
                    );
                } else {
                    assertNull(info.details().password());
                    assertNull(listing.password());
                    assertArrayEquals(
                        HexFormat.of().parseHex("000100"),
                        Arrays.copyOfRange(encodedInfo, encodedInfo.length - 3, encodedInfo.length)
                    );
                    assertEquals(0, encodedList[encodedList.length - 1]);
                }
            }
        }
    }
}
