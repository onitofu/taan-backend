package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.match.protocol.packet.GameResult;
import taan.online.match.protocol.packet.GameResultSlot;
import taan.online.match.protocol.packet.GameSlotPlayInfo;
import taan.online.match.protocol.packet.LoadingPosition;
import taan.online.match.protocol.packet.PcGamePlayLeaveUser;
import taan.online.match.protocol.packet.PcGamePlayLoadingDone;
import taan.online.match.protocol.packet.PcGamePlaySetPos;
import taan.online.match.protocol.packet.PcGamePlayTurnStart;
import taan.online.match.protocol.packet.PcGameSessionEndGame;
import taan.online.match.protocol.packet.PcGameSessionStartGame;
import taan.online.match.protocol.packet.PqGameSessionReportEnemies;
import taan.online.match.protocol.packet.ReportedEnemyPair;
import taan.online.protocol.MalformedPacketException;

class MatchMalformedCodecTest {
    @Test
    void rejectsBadStartCountsDuplicateSlotsNestedTypesAndUnsupportedFacing() throws Exception {
        var codec = new StartGameCodec();
        byte[] root = MatchWireFixtures.fixture("native-policy-v4-start-0.root.hex");

        for (int count : new int[]{0, 9, -1}) {
            byte[] broken = MatchWireFixtures.integer(root, 6, count);

            assertThrows(MalformedPacketException.class, () -> codec.decode(broken));
        }

        assertThrows(MalformedPacketException.class, () -> codec.decode(MatchWireFixtures.integer(root, 10, 0)));
        assertThrows(MalformedPacketException.class, () -> codec.decode(MatchWireFixtures.octet(root, 14, 8)));
        assertThrows(MalformedPacketException.class, () -> codec.decode(MatchWireFixtures.octet(root, 23, 2)));
        assertThrows(
            MalformedPacketException.class,
            () -> codec.decode(MatchWireFixtures.octet(root, 48, root[14]))
        );

        var parsed = codec.decode(root);

        for (var players : List.of(
            List.<GameSlotPlayInfo>of(),
            Collections.nCopies(9, parsed.players().getFirst()),
            Collections.nCopies(2, parsed.players().getFirst())
        )) {
            assertThrows(
                MalformedPacketException.class,
                () -> codec.encode(new PcGameSessionStartGame(1, players, 0, 0, List.of(255, 255, 255), 0))
            );
        }

        assertThrows(
            MalformedPacketException.class,
            () -> codec.encode(new PcGameSessionStartGame(1, parsed.players(), 0, 0, List.of(), 0))
        );
    }

    @Test
    void rejectsNegativeOrOutOfRangeSlotsAndFacingDuringEncoding() {
        for (int slot : new int[]{-1, 8}) {
            assertThrows(
                MalformedPacketException.class,
                () -> new SetPosCodec().encode(new PcGamePlaySetPos(slot, 0, 0, 0))
            );
            assertThrows(
                MalformedPacketException.class,
                () -> new LeaveUserCodec().encode(new PcGamePlayLeaveUser(slot))
            );
            assertThrows(
                MalformedPacketException.class,
                () -> new TurnStartCodec().encode(new PcGamePlayTurnStart(slot, 0, 0, 0, 0, 0, 0, 0, 0, 0))
            );
        }

        for (int facing : new int[]{-1, 2}) {
            assertThrows(
                MalformedPacketException.class,
                () -> new SetPosCodec().encode(new PcGamePlaySetPos(0, 0, 0, facing))
            );
            var player = new GameSlotPlayInfo(0, 1, 2, facing, 1000, 1000, 0, 3, 100, 0, 0);

            assertThrows(
                MalformedPacketException.class,
                () -> new StartGameCodec().encode(
                    new PcGameSessionStartGame(1, List.of(player), 0, 0, List.of(255, 255, 255), 0)
                )
            );
        }
    }

    @Test
    void rejectsOversizedDuplicateOrTruncatedLoadingAndEnemyReports() throws Exception {
        var loading = new LoadingDoneCodec();
        byte[] root = MatchWireFixtures.fixture("native-policy-v4-loading-0.root.hex");

        assertThrows(MalformedPacketException.class, () -> loading.decode(MatchWireFixtures.octet(root, 4, 3)));
        var duplicate = new PcGamePlayLoadingDone(List.of(new LoadingPosition(1, 2, 3), new LoadingPosition(1, 4, 5)));

        assertThrows(MalformedPacketException.class, () -> loading.encode(duplicate));
        assertThrows(
            MalformedPacketException.class,
            () -> loading.encode(new PcGamePlayLoadingDone(Collections.nCopies(3, new LoadingPosition(0, 1, 2))))
        );

        byte[] two = loading.encode(
            new PcGamePlayLoadingDone(List.of(new LoadingPosition(0, 1, 2), new LoadingPosition(1, 3, 4)))
        );

        assertThrows(MalformedPacketException.class, () -> loading.decode(MatchWireFixtures.octet(two, 14, 0)));
        assertThrows(MalformedPacketException.class, () -> loading.decode(MatchWireFixtures.octet(root, 5, 8)));

        var reports = new ReportEnemiesCodec();
        byte[] report = MatchWireFixtures.fixture("native-policy-v4-report-enemies-0.root.hex");

        for (int count : new int[]{9, -1}) {
            assertThrows(
                MalformedPacketException.class,
                () -> reports.decode(MatchWireFixtures.integer(report, 4, count))
            );
        }

        assertThrows(
            MalformedPacketException.class,
            () -> reports.encode(new PqGameSessionReportEnemies(Collections.nCopies(9, new ReportedEnemyPair(0, 0))))
        );
    }

    @Test
    void rejectsNonemptyTurnVectorsAndMalformedResultObjectsWithoutAllocatingTheirCounts() throws Exception {
        var turns = new TurnStartCodec();
        byte[] turn = MatchWireFixtures.fixture("native-policy-v4-first-turn-0.root.hex");

        for (int offset : new int[]{13, 17, 21}) {
            assertThrows(
                MalformedPacketException.class,
                () -> turns.decode(MatchWireFixtures.integer(turn, offset, -1))
            );
        }

        assertThrows(MalformedPacketException.class, () -> turns.decode(MatchWireFixtures.octet(turn, 4, 8)));
        byte[] pose = MatchWireFixtures.fixture("native-policy-v4-setpos-0.root.hex");

        assertThrows(
            MalformedPacketException.class,
            () -> new SetPosCodec().decode(MatchWireFixtures.octet(pose, 13, 2))
        );
        byte[] leave = MatchWireFixtures.fixture("leave-user-slot2.root.hex");

        assertThrows(
            MalformedPacketException.class,
            () -> new LeaveUserCodec().decode(MatchWireFixtures.octet(leave, 4, 8))
        );

        var ends = new EndGameCodec();
        byte[] end = MatchWireFixtures.fixture("end-game-result0-no-awards.root.hex");

        for (int offset : new int[]{4, 9, 13, 24, 45}) {
            assertThrows(
                MalformedPacketException.class,
                () -> ends.decode(MatchWireFixtures.integer(end, offset, -1))
            );
        }

        assertThrows(MalformedPacketException.class, () -> ends.decode(MatchWireFixtures.octet(end, 17, 8)));
        assertThrows(MalformedPacketException.class, () -> ends.decode(MatchWireFixtures.octet(end, 17, 1)));

        var result = ends.decode(end).result();

        assertThrows(
            MalformedPacketException.class,
            () -> ends.encode(new PcGameSessionEndGame(new GameResult(0, List.of(), 0, 0), 0))
        );

        var slots = new ArrayList<>(result.slots());
        slots.set(1, slots.getFirst());

        assertThrows(
            MalformedPacketException.class,
            () -> ends.encode(new PcGameSessionEndGame(new GameResult(0, slots, 0, 0), 0))
        );

        slots.set(1, result.slots().get(1));
        slots.set(0, new GameResultSlot(0, 0, 0, 0, 0, 0, 0, 0, List.of(), 0, 0, 0));

        assertThrows(
            MalformedPacketException.class,
            () -> ends.encode(new PcGameSessionEndGame(new GameResult(0, slots, 0, 0), 0))
        );
    }
}
