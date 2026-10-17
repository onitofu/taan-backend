package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static taan.online.ChannelFixtures.fixture;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.channel.protocol.codec.MoveToRequestCodec;
import taan.online.channel.protocol.codec.MoveToResponseCodec;
import taan.online.channel.protocol.packet.ChannelInfo;
import taan.online.channel.protocol.packet.PrChannelMoveTo;
import taan.online.lobby.protocol.codec.LobbyRequestCodec;
import taan.online.lobby.protocol.codec.LobbyResponseCodec;
import taan.online.lobby.protocol.packet.PcLobbyInitRoomList;
import taan.online.lobby.protocol.packet.PcLobbyInitUserList;
import taan.online.lobby.protocol.packet.PcLobbyUpdateUserList;
import taan.online.lobby.protocol.packet.PrLobbyEnter;
import taan.online.lobby.protocol.packet.PrLobbyLeave;
import taan.online.lobby.protocol.packet.UserSimpleInfo;
import taan.online.protocol.MalformedPacketException;
import taan.online.session.protocol.codec.PlayableTimeCodec;
import taan.online.session.protocol.packet.PcNotifyPlayableTime;

class ChannelWireTest {
    @Test
    void matchesIndependentMoveRequestsSuccessAndFullFailureLayouts() throws Exception {
        var request = new MoveToRequestCodec().decode(fixture("move-to-request-normal"));

        assertEquals(0, request.mode());
        assertEquals(7, request.channelId());
        assertEquals(0, request.followArgument());
        assertEquals(929, request.clientRevision());
        assertEquals(1, new MoveToRequestCodec().decode(fixture("020-PqChannel_MoveTo")).channelId());

        for (int result : new int[]{0, 100, 101, 102, 103, 255}) {
            var info = result == 0 || result == 101 || result == 102
                ? new ChannelInfo("Local", 7, 2181, 0, 1, 109, false) : null;
            String name = result == 0 ? "move-to-success-0" : "move-to-failure-" + result;

            assertArrayEquals(
                fixture(name),
                new MoveToResponseCodec().encode(
                    new PrChannelMoveTo(result, 7, 2181, info)
                )
            );
        }

        var disabled = new MoveToResponseCodec().encode(
            new PrChannelMoveTo(
                103,
                7,
                2181,
                new ChannelInfo("Local", 7, 2181, 100, 1, 109, true)
            )
        );

        assertEquals(1, disabled[disabled.length - 1]);
        assertArrayEquals(
            fixture("PcNotifyPlayableTime-one-hour.packet"),
            new PlayableTimeCodec().encode(new PcNotifyPlayableTime(3600))
        );
    }

    @Test
    void matchesRoomRosterDeltaAndLeaveFixturesIncludingUtf16SurrogatePairs() throws Exception {
        var codec = new LobbyResponseCodec();

        assertArrayEquals(fixture("lobby-enter-response-0"), codec.enter(new PrLobbyEnter(1, 0)));
        assertArrayEquals(fixture("lobby-enter-response-4"), codec.enter(new PrLobbyEnter(1, 4)));
        assertArrayEquals(fixture("PcLobby_InitRoomList-empty"), codec.rooms(new PcLobbyInitRoomList()));
        assertArrayEquals(
            fixture("PcLobby_InitUserList-two"),
            codec.users(
                new PcLobbyInitUserList(
                    List.of(
                        new UserSimpleInfo(1001, "HeroA", 1, 0),
                        new UserSimpleInfo(1002, "Герой😀", 109, 0)
                    )
                )
            )
        );
        assertArrayEquals(
            fixture("PcLobby_UpdateUserList-add"),
            codec.update(
                new PcLobbyUpdateUserList(
                    List.of(new UserSimpleInfo(1002, "HeroB", 1, 0)),
                    List.of()
                )
            )
        );
        assertArrayEquals(
            fixture("PcLobby_UpdateUserList-remove"),
            codec.update(
                new PcLobbyUpdateUserList(
                    List.of(),
                    List.of(
                        1002L
                    )
                )
            )
        );
        assertArrayEquals(
            fixture("PcLobby_UpdateUserList-replace"),
            codec.update(
                new PcLobbyUpdateUserList(
                    List.of(new UserSimpleInfo(1001, "HeroA", 2, 0)),
                    List.of(1001L)
                )
            )
        );
        assertArrayEquals(fixture("lobby-leave-response"), codec.leave(new PrLobbyLeave()));

        var users = new PcLobbyInitUserList(List.of());
        var update = new PcLobbyUpdateUserList(List.of(), List.of());

        assertThrows(UnsupportedOperationException.class, () -> users.users().clear());
        assertThrows(UnsupportedOperationException.class, () -> update.added().clear());
        assertThrows(UnsupportedOperationException.class, () -> update.removed().clear());
    }

    @Test
    void rejectsEveryRequestTruncationTrailingByteAndUnsupportedLobbyField() throws Exception {
        for (String name : List.of(
            "move-to-request-normal",
            "lobby-enter-request-0",
            "lobby-enter-request-1",
            "lobby-leave-request"
        )) {
            byte[] root = fixture(name);

            for (int length = 0; length < root.length; length++) {
                byte[] truncated = Arrays.copyOf(root, length);

                assertThrows(MalformedPacketException.class, () -> decode(name, truncated));
            }

            assertThrows(MalformedPacketException.class, () -> decode(name, Arrays.copyOf(root, root.length + 1)));
            decode(name, root);
        }

        for (int index : new int[]{4, 5, 6}) {
            byte[] root = fixture("lobby-enter-request-0");
            root[index] = 2;

            assertThrows(MalformedPacketException.class, () -> new LobbyRequestCodec().enter(root));
        }
    }

    private void decode(String name, byte[] root) {
        if (name.startsWith("move")) {
            new MoveToRequestCodec().decode(root);
        } else if (name.startsWith("lobby-enter")) {
            new LobbyRequestCodec().enter(root);
        } else {
            new LobbyRequestCodec().leave(root);
        }
    }
}
