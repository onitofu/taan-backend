package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.TestLevels;
import taan.online.match.protocol.packet.GhostMove;
import taan.online.match.protocol.packet.PcGamePlayDeathByDrop;
import taan.online.match.protocol.packet.PcGamePlayMakeGhost;
import taan.online.match.protocol.packet.PcGamePlaySetGhostPos;
import taan.online.match.protocol.packet.PcGamePlaySetGhostTarget;
import taan.online.match.protocol.packet.PcGamePlayTurnStart;
import taan.online.match.service.model.MatchGhostNotice;
import taan.online.match.service.model.MatchGhostNoticeKind;
import taan.online.match.view.ProtocolMatchView;
import taan.online.protocol.MalformedPacketException;

class GhostCodecTest {
    @Test
    void matchesIndependentResearchVectorsAndRejectsTruncationAndInvalidSlots() throws Exception {
        var make = new MakeGhostCodec();
        var position = new SetGhostPosCodec();
        var target = new SetGhostTargetCodec();
        byte[] created = root("PcGamePlay_MakeGhost.synthetic.hex");
        byte[] placed = root("PcGamePlay_SetGhostPos.synthetic.hex");
        byte[] targeted = root("PcGamePlay_SetGhostTarget.synthetic.hex");
        assertEquals(new PcGamePlayMakeGhost(1, 100, 200, 1), make.decode(created));
        assertEquals(new PcGamePlaySetGhostPos(1, 100, 200, 1), position.decode(placed));
        assertEquals(new PcGamePlaySetGhostTarget(1, 100, 200), target.decode(targeted));
        assertArrayEquals(created, make.encode(make.decode(created)));
        assertArrayEquals(placed, position.encode(position.decode(placed)));
        assertArrayEquals(targeted, target.encode(target.decode(targeted)));

        for (int length = 0; length < created.length; length++) {
            byte[] bytes = Arrays.copyOf(created, length);
            assertThrows(MalformedPacketException.class, () -> make.decode(bytes));
        }

        for (int length = 0; length < placed.length; length++) {
            byte[] bytes = Arrays.copyOf(placed, length);
            assertThrows(MalformedPacketException.class, () -> position.decode(bytes));
        }

        for (int length = 0; length < targeted.length; length++) {
            byte[] bytes = Arrays.copyOf(targeted, length);
            assertThrows(MalformedPacketException.class, () -> target.decode(bytes));
        }

        assertThrows(MalformedPacketException.class, () -> make.encode(new PcGamePlayMakeGhost(8, 1, 2, 0)));
        assertThrows(MalformedPacketException.class, () -> position.encode(new PcGamePlaySetGhostPos(0, 1, 2, 2)));
        assertThrows(MalformedPacketException.class, () -> target.encode(new PcGamePlaySetGhostTarget(-1, 1, 2)));
        assertThrows(MalformedPacketException.class, () -> make.decode(Arrays.copyOf(created, created.length + 1)));
        assertThrows(MalformedPacketException.class, () -> position.decode(Arrays.copyOf(placed, placed.length + 1)));
        assertThrows(MalformedPacketException.class, () -> target.decode(Arrays.copyOf(targeted, targeted.length + 1)));
    }

    @Test
    void supportsTypedTurnMovesAndRejectsWrongTypesAndDuplicateSlots() throws Exception {
        var codec = new TurnStartCodec();
        byte[] bytes = root("TurnStart-ghost.synthetic.hex");
        var decoded = codec.decode(bytes);
        assertEquals(List.of(new GhostMove(1, 100, 200, 400, 500)), decoded.ghostMoves());
        assertArrayEquals(bytes, codec.encode(decoded));
        bytes[21] ^= 1;
        assertThrows(MalformedPacketException.class, () -> codec.decode(bytes));
        var duplicate = new GhostMove(1, 0, 0, 1, 1);
        assertThrows(MalformedPacketException.class, () -> codec.encode(new PcGamePlayTurnStart(
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            List.of(duplicate, duplicate)
        )));
    }

    @Test
    void deliversAllThreeGhostNotificationsThroughTheProtocolView() throws Exception {
        var output = new CollectingPacketOutput();
        var view = new ProtocolMatchView(output, TestLevels.table());
        view.ghost(new MatchGhostNotice(MatchGhostNoticeKind.CREATED, 1, 100, 200, 1));
        view.ghost(new MatchGhostNotice(MatchGhostNoticeKind.POSITION, 1, 100, 200, 1));
        view.ghost(new MatchGhostNotice(MatchGhostNoticeKind.TARGET, 1, 100, 200, 0));
        assertArrayEquals(root("PcGamePlay_MakeGhost.synthetic.hex"), output.batches.get(0).getFirst());
        assertArrayEquals(root("PcGamePlay_SetGhostPos.synthetic.hex"), output.batches.get(1).getFirst());
        assertArrayEquals(root("PcGamePlay_SetGhostTarget.synthetic.hex"), output.batches.get(2).getFirst());
    }

    @Test
    void matchesTheIndependentDropNoticeAndRejectsMalformedBodies() throws Exception {
        var codec = new DeathByDropCodec();
        byte[] bytes = root("PcGamePlay_DeathByDrop.synthetic.hex");
        assertEquals(new PcGamePlayDeathByDrop(1, 100, 200), codec.decode(bytes));
        assertArrayEquals(bytes, codec.encode(codec.decode(bytes)));

        for (int length = 0; length < bytes.length; length++) {
            var truncated = Arrays.copyOf(bytes, length);
            assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
        }

        assertThrows(MalformedPacketException.class, () -> codec.encode(new PcGamePlayDeathByDrop(8, 100, 200)));
        assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        var output = new CollectingPacketOutput();
        var notice = new MatchGhostNotice(MatchGhostNoticeKind.DROP, 1, 100, 200, 0);
        new ProtocolMatchView(output, TestLevels.table()).ghost(notice);
        assertArrayEquals(bytes, output.batches.getFirst().getFirst());
    }

    private static byte[] root(String name) throws Exception {
        try (var input = GhostCodecTest.class.getResourceAsStream("/match/ghost/" + name)) {
            return HexFormat.of().parseHex(new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim()
                .replace(" ", ""));
        }
    }
}
