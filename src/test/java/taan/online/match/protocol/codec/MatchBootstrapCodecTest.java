package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.match.protocol.packet.GameSlotPlayInfo;
import taan.online.match.protocol.packet.LoadingPosition;
import taan.online.match.protocol.packet.PcGamePlayLoadingDone;
import taan.online.match.protocol.packet.PcGamePlaySetPos;
import taan.online.match.protocol.packet.PcGamePlayTurnStart;
import taan.online.match.protocol.packet.PcGameSessionStartGame;
import taan.online.match.protocol.packet.PqGameSessionReportEnemies;
import taan.online.match.protocol.packet.ReportedEnemyPair;

class MatchBootstrapCodecTest {
    @Test
    void matchesCapturedStartLoadingReportsPositionsAndFirstTurn() throws Exception {
        var starts = new StartGameCodec();
        var start = MatchWireFixtures.golden("native-policy-v4-start-0.root.hex", starts::decode, starts::encode);

        assertEquals(1, start.mapId());
        assertEquals(2, start.players().size());
        assertEquals(-21, start.initialWindRaw());
        assertEquals(List.of(255, 255, 255), start.teamLeaderSlots());
        assertEquals(91, starts.encode(start).length);

        var loading = new LoadingDoneCodec();
        var positions = new SetPosCodec();
        var reports = new ReportEnemiesCodec();

        for (int index = 0; index < 2; index++) {
            var loaded = MatchWireFixtures.golden(
                "native-policy-v4-loading-" + index + ".root.hex",
                loading::decode,
                loading::encode
            );
            var pose = MatchWireFixtures.golden(
                "native-policy-v4-setpos-" + index + ".root.hex",
                positions::decode,
                positions::encode
            );
            var report = MatchWireFixtures.golden(
                "native-policy-v4-report-enemies-" + index + ".root.hex",
                reports::decode,
                reports::encode
            );

            assertEquals(1, loaded.positions().size());
            assertEquals(1, report.enemies().size());
            assertEquals(index == 0 ? 2 : 0, loaded.positions().getFirst().slotIndex());
            assertEquals(index == 0 ? 0 : 2, pose.slotIndex());
            assertEquals(0, report.enemies().getFirst().opaqueB());
        }

        var turns = new TurnStartCodec();
        var turn = MatchWireFixtures.golden("native-policy-v4-first-turn-0.root.hex", turns::decode, turns::encode);

        assertEquals(2, turn.activeSlot());
        assertEquals(-21, turn.windRawTenths());
        assertEquals(0, turn.energyGrant());
        assertEquals(1, turn.boxAttemptBudget());
        assertEquals(100, turn.pickerBase());
        assertEquals(43, turns.encode(turn).length);
        var example = MatchWireFixtures.golden(
            "first-turn-slot0-grant0-policy-example.root.hex",
            turns::decode,
            turns::encode
        );

        assertEquals(0, example.energyGrant());
    }

    @Test
    void preservesSignedCoordinatesWindsAndAllScalarFieldsWithoutApplyingGameplayPolicy() {
        var starts = new StartGameCodec();
        var player = new GameSlotPlayInfo(
            7,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            1,
            123,
            456,
            0xffffffffL,
            65535,
            65535,
            65535,
            65535
        );
        var message = new PcGameSessionStartGame(65535, List.of(player), -50, 65535, List.of(7, 0, 255), 0xffffffffL);

        assertEquals(message, starts.decode(starts.encode(message)));

        var turns = new TurnStartCodec();

        for (int wind : new int[]{-50, 0, 50, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            var turn = new PcGamePlayTurnStart(7, 255, wind, 65535, 255, 0xffffffffL, 1, 2, 3, 65535);

            assertEquals(turn, turns.decode(turns.encode(turn)));
        }

        var poses = new SetPosCodec();
        var pose = new PcGamePlaySetPos(7, Integer.MIN_VALUE, Integer.MAX_VALUE, 0);

        assertEquals(pose, poses.decode(poses.encode(pose)));
    }

    @Test
    void handlesEmptyAndTwoLocalLoadingEntriesAndEightOpaquePairsAsWireCapabilities() {
        var loading = new LoadingDoneCodec();

        for (var positions : List.of(
            List.<LoadingPosition>of(),
            List.of(new LoadingPosition(0, -1, 0), new LoadingPosition(7, 1, -2))
        )) {
            var message = new PcGamePlayLoadingDone(positions);

            assertEquals(message, loading.decode(loading.encode(message)));
        }

        var reports = new ReportEnemiesCodec();
        var pairs = new ArrayList<ReportedEnemyPair>();
        var empty = new PqGameSessionReportEnemies(pairs);

        assertEquals(empty, reports.decode(reports.encode(empty)));

        for (int index = 0; index < 8; index++) {
            pairs.add(new ReportedEnemyPair(0xffffffffL, index));
        }

        var full = new PqGameSessionReportEnemies(pairs);
        pairs.clear();

        assertEquals(8, full.enemies().size());
        assertEquals(full, reports.decode(reports.encode(full)));
        assertThrows(UnsupportedOperationException.class, () -> full.enemies().clear());
        assertThrows(UnsupportedOperationException.class, () -> empty.enemies().add(new ReportedEnemyPair(1, 2)));
    }

    @Test
    void copiesWireCollectionsBeforeCallersCanChangeTheirContents() {
        var players = new ArrayList<GameSlotPlayInfo>();
        var leaders = new ArrayList<>(List.of(255, 255, 255));
        var player = new GameSlotPlayInfo(0, 97, 686, 0, 1000, 1000, 0, 3, 100, 0, 0);
        players.add(player);
        var start = new PcGameSessionStartGame(1, players, 0, 0, leaders, 0);
        players.clear();
        leaders.clear();
        var loading = new PcGamePlayLoadingDone(new ArrayList<>(List.of(new LoadingPosition(0, 1, 2))));

        assertEquals(List.of(player), start.players());
        assertEquals(List.of(255, 255, 255), start.teamLeaderSlots());
        assertThrows(UnsupportedOperationException.class, () -> start.players().clear());
        assertThrows(UnsupportedOperationException.class, () -> start.teamLeaderSlots().clear());
        assertThrows(UnsupportedOperationException.class, () -> loading.positions().clear());
    }
}
